public class Message {

  String type;
  int proposalNumber;
  String value;

  public Message(String type, int proposalNumber, String value) {
    this.type = type;
    this.proposalNumber = proposalNumber;
    this.value = value;
  }

  @Override
  public String toString() {
    return type + "," + proposalNumber + "," + (value != null ? value : "");
  }

  public static Message fromString(String str) {
    String[] parts = str.split(",", 3);
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid message format: " + str);
    }
    String type = parts[0];
    int proposalNumber = Integer.parseInt(parts[1]);
    String value = parts.length > 2 ? parts[2] : "";
    return new Message(type, proposalNumber, value);
  }
}
