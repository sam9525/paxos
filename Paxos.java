import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Paxos {

  public static void main(String[] args) throws InterruptedException {
    List<Member> councilMembers = new ArrayList<>();
    Random random = new Random();

    // create all 9 members
    for (int i = 1; i <= 9; i++) {
      Member member;

      // random network conditions
      int randomLatency = 50 + random.nextInt(200); // Random latency between 50-250ms
      member = new Member(i, councilMembers, randomLatency, 1.0);

      member.startServer();
      councilMembers.add(member);
      Thread.sleep(100);
    }

    Thread.sleep(1000);

    System.exit(0);
  }
}