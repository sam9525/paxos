# Paxos

## Member

`int id` - member's id
`List<Member> members` - stores all the members
`int latencyMs` - base network latency in milliseconds
`double reliability` - network probability of successful transmission
`double randomResponse` - the random response of member

### Start Server

Start the server for the member. Create a serversocket on a port the is 5000 plus the member's id. If successful, it prints a message.

```
serverSocket = new ServerSocket(5000 + id);
```

Loop continuously accepts incoming connections and handles each client connection by calling the handleClient method.

```
while (isRunning && !Thread.currentThread().isInterrupted()) {
  try (Socket socket = serverSocket.accept()) {
    handleClient(socket);
  }
}
```

### handleClient

Create socket's input and output stream.

### handleRequest

Handles incoming requests from clients.

If the new proposal number is bigger than promised proposal then send the message.

```
if (
  message.proposalNumber > promisedProposal &&
  random.nextDouble() < randomResponse
) {
  promisedProposal = message.proposalNumber;
  out.println(
    new Message("PROMISE", promisedProposal, "").toString()
  );
} else {
  out.println(
    new Message("REFUSE", message.proposalNumber, "").toString()
  );
}
```

### propose

Increment the proposal number that based on GLOBAL_PROPOSAL_NUMBER.

```
int proposalNumber = GLOBAL_PROPOSAL_NUMBER.incrementAndGet();
```

The member send a prepare message to all members, if the promised members more than 5, then send a accept message to all members, if the accepted members more than 5, all members reached consensus and learn the proposal.

### sendPrepareToAll

Sends a prepare messgae to all other members by using sendPrepare method and collects their responses they would either agree or not.

Clear promised members, then store all new promised member.

```
promisedMembers.clear();
```

### sendPrepare

Sends a prepare message to a specified member.

Using sendMessage to send the message.

### sendAcceptToAll

Sends a accept messgae to all other members by using sendAccept method and collects their responses they would either agree or not.

### sendAccept

Sends a accept message to a specified member.

Using sendMessage to send the message.

### learn

When the proposal has been accepted broadcast it to other members.

### sendLearnedToAll

Sends a learn messgae to all other members by using sendLearned method and collects their responses they would either agree or not.

### sendLearned

Sends a learn message to a specified member.

Using sendMessage to send the message.

### sendMessage

Sends a message to a specified member and returns true if the message is accepted or promised, false otherwise.

### simulateNetworkConditions

Simulates network conditions by introducing latency and potential network failures.

```
private final int latencyMs;
private final double reliability;
```

[!NOTE]
The testing should be run separately.
