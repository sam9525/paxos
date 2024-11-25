import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class Paxos_Test {

  private List<Member> members;

  // @Before
  // public void setUp() {
  //   members = new ArrayList<>();
  //   // create 9 members
  //   for (int i = 1; i <= 9; i++) {
  //     Member member = new Member(i, members, 0, 1.0, 1.0);
  //     member.startServer();
  //     members.add(member);
  //     try {
  //       Thread.sleep(100);
  //     } catch (InterruptedException e) {
  //       e.printStackTrace();
  //     }
  //   }
  // }

  // @Test
  // public void testMessageCreation() {
  //   Message message = new Message("PREPARE", 1, "test value");
  //   assertEquals("PREPARE", message.type);
  //   assertEquals(1, message.proposalNumber);
  //   assertEquals("test value", message.value);
  // }

  // @Test
  // public void testMessageSerialization() {
  //   Message original = new Message("PREPARE", 1, "test value");
  //   String serialized = original.toString();
  //   Message deserialized = Message.fromString(serialized);

  //   assertEquals(original.type, deserialized.type);
  //   assertEquals(original.proposalNumber, deserialized.proposalNumber);
  //   assertEquals(original.value, deserialized.value);
  // }

  @Test
  public void testSingleProposal() throws InterruptedException {
    members = new ArrayList<>();
    // create 9 members
    for (int i = 1; i <= 9; i++) {
      Member member = new Member(i, members, 0, 1.0, 1.0);
      member.startServer();
      members.add(member);
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    Member proposer = members.get(0);
    proposer.propose("Proposal 1");

    Thread.sleep(2000);

    // check that all acceptors have promised the proposal
    for (Member acceptor : members.subList(1, 8)) {
      assertEquals(1, acceptor.getPromisedProposal());
    }

    // check that the proposer has accepted the proposal
    assertEquals("Proposal 1", proposer.getAcceptedProposal().value);

    // check that all members have learned the proposal
    for (Member member : members) {
      assertEquals("Proposal 1", member.getAcceptedProposal().value);
    }

    Member proposer2 = members.get(1);
    proposer2.propose("Proposal 2");

    // check that all acceptors have promised the proposal
    for (Member acceptor : members.subList(2, 8)) {
      assertEquals(2, acceptor.getPromisedProposal());
    }

    // check that the proposer has accepted the proposal
    assertEquals("Proposal 2", proposer.getAcceptedProposal().value);

    // check that all members have learned the proposal
    for (Member member : members) {
      assertEquals("Proposal 2", member.getAcceptedProposal().value);
      member.stopServer();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidMessageFormat() {
    Message.fromString("TEST");
  }

  @Test
  public void testPrepareFailure() {
    // members.clear();
    members = new ArrayList<>();
    Member member;

    for (int i = 1; i <= 9; i++) {
      if (i <= 5) {
        member = new Member(i, members, 0, 1.0, 0.0);
      } else {
        member = new Member(i, members, 0, 1.0, 1.0);
      }
      member.startServer();
      members.add(member);
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Member proposer = members.get(0);
    Member acceptor = members.get(1);

    proposer.propose("Proposal 1");
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    assertEquals(-1, acceptor.getPromisedProposal());
  }
}
