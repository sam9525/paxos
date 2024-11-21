import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Member {

  private static final AtomicInteger GLOBAL_PROPOSAL_NUMBER = new AtomicInteger(
    0
  );

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private int id;
  private List<Member> members;
  private final int latencyMs; // base latency in milliseconds
  private final double reliability; // probability of successful transmission (0.0 to 1.0)
  private final Random random = new Random();

  private int promisedProposal = -1;
  private final Set<Integer> promisedMembers = new HashSet<>();
  private volatile Proposal acceptedProposal = null;

  private ServerSocket serverSocket;
  private volatile boolean isRunning = true;

  public Member(
    int id,
    List<Member> members,
    int latencyMs,
    double reliability
  ) {
    this.id = id;
    this.members = members;
    this.latencyMs = latencyMs;
    this.reliability = reliability;
  }

  // start all members server
  public void startServer() {
    executor.submit(() -> {
      try {
        serverSocket = new ServerSocket(5000 + id);
        System.out.println(
          "Server started for member " + id + " at localhost:" + (5000 + id)
        );
        while (isRunning && !Thread.currentThread().isInterrupted()) {
          try (Socket socket = serverSocket.accept()) {
            handleClient(socket);
          }
        }
      } catch (IOException e) {
        if (isRunning) {
          System.out.println(
            "Error in server thread for member " + id + ": " + e
          );
        }
      }
    });
  }

  // create socket's input and output stream
  private void handleClient(Socket socket) {
    try (
      // socket's input stream
      BufferedReader in = new BufferedReader(
        new InputStreamReader(socket.getInputStream())
      );
      // socket's output stream
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
    ) {
      String messageStr = in.readLine();
      handleRequest(messageStr, out);
    } catch (IOException e) {
      System.out.println("Error handling client for member " + id + ": " + e);
    }
  }

  // handle PREPARE and ACCEPT request
  /**
   * Handles incoming requests from clients. This method processes the request based on its type,
   * which can be either "PREPARE" or "ACCEPT". It responds accordingly to the client.
   *
   * @param messageStr The string representation of the incoming message.
   * @param out The PrintWriter used to send responses back to the client.
   */
  private void handleRequest(String messageStr, PrintWriter out) {
    try {
      Message message = Message.fromString(messageStr);

      switch (message.type) {
        case "PREPARE":
          if (message.proposalNumber > promisedProposal) {
            promisedProposal = message.proposalNumber;
            out.println(
              new Message("PROMISE", promisedProposal, "").toString()
            );
          } else {
            out.println(
              new Message("REFUSE", message.proposalNumber, "").toString()
            );
          }
          break;
        case "ACCEPT":
          if (message.proposalNumber >= promisedProposal) {
            acceptedProposal =
              new Proposal(message.proposalNumber, message.value);
            out.println(
              new Message(
                "ACCEPTED",
                acceptedProposal.proposalNumber,
                acceptedProposal.value
              )
                .toString()
            );
          } else {
            out.println(
              new Message("REFUSE", message.proposalNumber, "").toString()
            );
          }
          break;
        case "LEARN":
          if (
            acceptedProposal == null ||
            message.proposalNumber >= acceptedProposal.proposalNumber
          ) {
            acceptedProposal =
              new Proposal(message.proposalNumber, message.value);
            System.out.println(
              "Member " + id + " learned consensus value: " + message.value
            );
          }
          out.println(
            new Message("LEARNED", message.proposalNumber, "").toString()
          );
          break;
        default:
          System.out.println("Unknown message type: " + message.type);
      }
    } catch (IllegalArgumentException e) {
      System.out.println("Invalid message received: " + e);
      out.println(new Message("ERROR", 0, e.getMessage()).toString());
    } catch (Exception e) {
      System.out.println("Error processing message: " + e);
      out.println(new Message("ERROR", 0, "Internal server error").toString());
    }
  }

  /**
   * Initiates a proposal for a value within the Paxos consensus protocol.
   * This involves sending PREPARE messages to all members, counting promises,
   * sending ACCEPT messages to all members, and counting accepts. If a majority
   * of members promise and accept, consensus is reached, and the value is learned.
   *
   * @param value The value being proposed.
   */
  public void propose(String value) {
    int proposalNumber = GLOBAL_PROPOSAL_NUMBER.incrementAndGet();
    System.out.println(
      "Member " +
      id +
      " proposing value: " +
      value +
      " with proposal number: " +
      proposalNumber
    );

    // send PREPARE request to all members
    List<Future<Boolean>> prepareFutures = sendPrepareToAll(proposalNumber);
    // counts promises
    int promises = countSuccessfulFutures(prepareFutures);
    System.out.println("Promises received: " + promises);
    if (promises > members.size() / 2) {
      List<Future<Boolean>> acceptFutures = sendAcceptToAll(
        proposalNumber,
        value
      );
      // Include self in the count (proposer counts as accepted)
      int acceptedCount = countSuccessfulFutures(acceptFutures);
      System.out.println("Accepts received: " + acceptedCount);
      if (acceptedCount > members.size() / 2) {
        learn(new Proposal(proposalNumber, value));
        System.out.println("Consensus reached on: " + value);
      } else {
        System.out.println(
          "Consensus not reached. M" + id + " did not get elected"
        );
      }
    } else {
      System.out.println(
        "Not enough promises received. M" + id + " did not get elected"
      );
    }
  }

  /**
   * Sends a PREPARE message to all other members and collects their responses.
   *
   * @param proposalNumber The proposal number to include in the PREPARE message.
   * @return A list of futures representing the responses from other members.
   */
  private List<Future<Boolean>> sendPrepareToAll(int proposalNumber) {
    List<Future<Boolean>> responses = new ArrayList<>();
    // clear previous promises
    promisedMembers.clear();

    for (Member member : members) {
      if (member.id != this.id) {
        Future<Boolean> response = executor.submit(() -> {
          boolean promised = sendPrepare(member.id, proposalNumber);
          if (promised) {
            promisedMembers.add(member.id);
          }
          return promised;
        });
        responses.add(response);
      }
    }
    return responses;
  }

  /**
   * Sends a ACCEPT message to all other members and collects their responses.
   *
   * @param proposalNumber The proposal number to include in the ACCEPT message.
   * @param value The value to include in the ACCEPT message.
   * @return A list of futures representing the responses from other members.
   */
  private List<Future<Boolean>> sendAcceptToAll(
    int proposalNumber,
    String value
  ) {
    List<Future<Boolean>> responses = new ArrayList<>();

    for (Member member : members) {
      if (member.id != this.id && promisedMembers.contains(member.id)) {
        Future<Boolean> response = executor.submit(() ->
          sendAccept(member.id, proposalNumber, value)
        );
        responses.add(response);
      }
    }

    return responses;
  }

  /**
   * Sends a LEARN message to all other members and collects their responses.
   *
   * @param proposalNumber The proposal number to include in the LEARN message.
   * @param value The value to include in the LEARN message.
   * @return A list of futures representing the responses from other members.
   */
  private List<Future<Boolean>> sendLearnedToAll(
    int proposalNumber,
    String value
  ) {
    List<Future<Boolean>> responses = new ArrayList<>();

    for (Member member : members) {
      if (member.id != this.id) {
        Future<Boolean> response = executor.submit(() ->
          sendLearned(member.id, proposalNumber, value)
        );
        responses.add(response);
      }
    }

    return responses;
  }

  /**
   * Counts the number of successful futures within a given timeout.
   *
   * @param futures A list of futures to check.
   * @return The number of successful futures.
   */
  private int countSuccessfulFutures(List<Future<Boolean>> futures) {
    int successCount = 0;
    for (Future<Boolean> future : futures) {
      try {
        if (future.get(5, TimeUnit.SECONDS)) {
          successCount++;
        }
      } catch (Exception e) {
        System.out.println("Future completion error: " + e.getMessage());
      }
    }
    return successCount;
  }

  /**
   * Sends a prepare message to a specified member.
   *
   * @param memberId The ID of the member to send the message to.
   * @param proposalNumber The proposal number of the message.
   * @return true if the message is successfully sent, false otherwise.
   */
  private boolean sendPrepare(int memberId, int proposalNumber) {
    return sendMessage(memberId, new Message("PREPARE", proposalNumber, ""));
  }

  /**
   * Sends a accept message to a specified member.
   *
   * @param memberId The ID of the member to send the message to.
   * @param proposalNumber The proposal number of the message.
   * @param value The value of the message.
   * @return true if the message is successfully sent, false otherwise.
   */
  private boolean sendAccept(int memberId, int proposalNumber, String value) {
    return sendMessage(memberId, new Message("ACCEPT", proposalNumber, value));
  }

  /**
   * Sends a learn message to a specified member.
   *
   * @param memberId The ID of the member to send the message to.
   * @param proposalNumber The proposal number of the message.
   * @param value The value of the message.
   * @return true if the message is successfully sent, false otherwise.
   */
  private boolean sendLearned(int memberId, int proposalNumber, String value) {
    return sendMessage(memberId, new Message("LEARN", proposalNumber, value));
  }

  /**
   * Sends a message to a specified member and returns true if the message is accepted or promised, false otherwise.
   *
   * @param memberId The ID of the member to send the message to.
   * @param message The message to be sent.
   * @return true if the message is accepted or promised, false otherwise.
   */
  private boolean sendMessage(int memberId, Message message) {
    try {
      // Simulate network conditions before sending the message
      simulateNetworkConditions();

      try (
        Socket socket = new Socket("localhost", 5000 + memberId);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
          new InputStreamReader(socket.getInputStream())
        )
      ) {
        out.println(message.toString());
        String response = in.readLine();
        if (response == null) {
          System.out.println("No response received from member " + memberId);
        }

        // handle both acceptance and refusal
        if (response.startsWith("REFUSE")) {
          System.out.println(
            "Member " + memberId + " refused the " + message.type
          );
          return false;
        }
        // Return true if the member promises or accepts the message
        return (
          response.startsWith("PROMISE") ||
          response.startsWith("ACCEPTED") ||
          response.startsWith("LEARN")
        );
      }
    } catch (IOException e) {
      System.out.println("Error communicating with member " + memberId + e);
    }
    return false;
  }

  /**
   * This method is responsible for learning a consensus value and broadcasting it to other members.
   * It updates the accepted proposal, sends LEARN messages to all members, and counts the responses.
   *
   * @param proposal The proposal to be learned and broadcasted.
   */
  private void learn(Proposal proposal) {
    this.acceptedProposal = proposal;
    System.out.println(
      "Member " + id + " learned consensus value: " + proposal.value
    );

    // send LEARN messages and wait for responses
    List<Future<Boolean>> learnResponses = sendLearnedToAll(
      proposal.proposalNumber,
      proposal.value
    );

    // wait for responses (optional, but helps ensure message delivery)
    int learnedCount = countSuccessfulFutures(learnResponses) + 1;
    System.out.println(
      "Learn message acknowledged by " + learnedCount + " members"
    );
  }

  /**
   * Simulates network conditions by introducing latency and potential network failures.
   *
   * This method simulates network conditions by introducing a random latency between 0 and the specified latencyMs.
   * It also simulates network failures by throwing an IOException with a certain probability based on the reliability parameter.
   *
   * @throws IOException if a network failure is simulated or if the thread is interrupted during simulation.
   */
  private void simulateNetworkConditions() throws IOException {
    // only simulate network conditions if latencyMs is greater than 0
    if (latencyMs > 0) {
      if (random.nextDouble() > reliability) {
        throw new IOException("Network failure (simulated)");
      }

      try {
        // random variation in latency
        Thread.sleep(latencyMs + random.nextInt(latencyMs));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted during network simulation");
      }
    }
  }
}
