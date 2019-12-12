import java.util.Set;
import java.util.HashSet;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNodeStupid implements Node {
    Set<Transaction> pending;

    public CompliantNodeStupid(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.pending = new HashSet<>();
    }

    public void setFollowees(boolean[] followees) {
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        for (Transaction tx : pendingTransactions) {
            pending.add(tx);
        }
    }

    public Set<Transaction> sendToFollowers() {
        return pending;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            pending.add(candidate.tx);
        }
    }
}
