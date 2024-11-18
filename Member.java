import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Member {

  private static final ExecutorService executor = Executors.newCachedThreadPool();
  private int id;
  private List<Member> members;
  private final int latencyMs; // base latency in milliseconds
  private final double reliability; // probability of successful transmission (0.0 to 1.0)
  private final Random random = new Random();

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

  public void startServer() {
    executor.submit(() -> {
      try {
        serverSocket = new ServerSocket(5000 + id);
        System.out.println(
          "Server started for member " + id + " at localhost:" + (5000 + id)
        );
      } catch (IOException e) {
        if (isRunning) {
          System.out.println(
            "Error in server thread for member " + id + ": " + e
          );
        }
      }
    });
  }
}
