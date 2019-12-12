import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.BitSet;
import java.util.Collections;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
    //private double p_malicious;
    private int numRounds;
    //private int total;

    //ArrayList<Integer> followees;
    int lowerbound;
    Map< Transaction, Integer > pending;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        //this.p_malicious = p_malicious;
        this.numRounds = numRounds;
        //this.total = numRounds;
        //this.followees = new ArrayList<>();
        this.lowerbound = 0;
        this.pending = new HashMap<>();
    }

    public void setFollowees(boolean[] followees) {
        for (int i = 0; i < followees.length; ++i) {
            if (followees[i]) {
                lowerbound++;
            }
        }
        lowerbound *= numRounds;
        lowerbound /= 4;
        //Collections.sort(this.followees);
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        for (Transaction tx : pendingTransactions) {
            if (!pending.containsKey(tx)) {
                pending.put(tx, 0);
            }
        }
    }

    public Set<Transaction> sendToFollowers() {
        if (numRounds == 0) {
            Set<Transaction> result = new HashSet<>();
            for (Transaction tx : pending.keySet()) {
                if (pending.get(tx) >= lowerbound) {
                    result.add(tx);
                }
            }
            return result;
        }
        --numRounds;

        return pending.keySet();
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            if (!pending.containsKey(candidate.tx)) {
                pending.put(candidate.tx, 0);
            }
            pending.put(candidate.tx, pending.get(candidate.tx) + 1);
        }
    }
}
