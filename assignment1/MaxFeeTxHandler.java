import java.util.*;
import java.lang.*;

public class MaxFeeTxHandler {

    private UTXOPool unspentPool;
    private UTXOPool tempPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        unspentPool = new UTXOPool(utxoPool);
        tempPool = new UTXOPool();
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx, boolean disableUnspentCheck) {
        // IMPLEMENT THIS

        double balance = 0;
        HashSet<UTXO> used = new HashSet<>();

        for (int i = 0; i < tx.numInputs(); ++i) {
            Transaction.Input currentInput = tx.getInput(i);
            UTXO utxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);

            if (disableUnspentCheck) {
                if (!unspentPool.contains(utxo) && !tempPool.contains(utxo)) return false; // condition (1)
            } else {
                if (!unspentPool.contains(utxo)) return false; // condition (1)
            }

            Transaction.Output previousOutput = unspentPool.contains(utxo) ? unspentPool.getTxOutput(utxo) : tempPool.getTxOutput(utxo);
            if (!Crypto.verifySignature(previousOutput.address, tx.getRawDataToSign(i), currentInput.signature)) return false; // condition (2)

            if (used.contains(utxo)) return false; // condition (3)
            used.add(utxo);

            balance += previousOutput.value;
        }

        for (int i = 0; i < tx.numOutputs(); ++i) {
            Transaction.Output currentOutput = tx.getOutput(i);
            if (currentOutput.value < 0) return false; // condition (4)
            balance -= currentOutput.value;
        }

        if (balance < 0) return false; // condition (5)

        return true;
    }

    public boolean isValidTx(Transaction tx) {
        return isValidTx(tx, false);
    }

    private double getFee(Transaction tx) {
        double balance = 0;
        for (int i = 0; i < tx.numInputs(); ++i) {
            Transaction.Input currentInput = tx.getInput(i);
            UTXO utxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);
            Transaction.Output previousOutput = unspentPool.contains(utxo) ? unspentPool.getTxOutput(utxo) : tempPool.getTxOutput(utxo);
            balance += previousOutput.value;
        }

        for (int i = 0; i < tx.numOutputs(); ++i) {
            Transaction.Output currentOutput = tx.getOutput(i);
            balance -= currentOutput.value;
        }

        return balance;
    }

    class Graph {
        private ArrayList< ArrayList<Integer> > a;
        private boolean[] isLeaf;
        private double[] value;
        private int n;

        private boolean[] visited;
        private boolean[] used;

        private HashSet<UTXO> tempUsedInputs;
        private ArrayList< HashSet<UTXO> > usedInputs;
        private HashMap< Integer, HashMap< Integer, HashSet<Integer> > > edgeIndexes;
        private ArrayList< HashSet<Integer> > usedIndex;

        public Graph(int n) {
            this.n = n;

            // init adjacency list
            a = new ArrayList<>();
            for (int i = 0; i < n; ++i) {
                a.add(new ArrayList<Integer>());
            }

            // init flags
            isLeaf = new boolean[n];
            value = new double[n];
            visited = new boolean[n];
            used = new boolean[n];
            for (int i = 0; i < n; ++i) {
                isLeaf[i] = false;
                value[i] = 0;
                visited[i] = false;
                used[i] = false;
            }

            usedInputs = new ArrayList<>();
            for (int i = 0; i < n; ++i) {
                usedInputs.add(new HashSet<UTXO>());
            }
            tempUsedInputs = new HashSet<>();
            edgeIndexes = new HashMap<>();
            usedIndex = new ArrayList<>();
            for (int i = 0; i < n; ++i) {
                usedIndex.add(new HashSet<>());
            }
        }

        void addUsedInput(int u, UTXO utxo) {
            usedInputs.get(u).add(utxo);
        }

        private double dfsGetValue(int u) {
            for (UTXO utxo : usedInputs.get(u)) {
                if (tempUsedInputs.contains(utxo)) {
                    return -1;
                }
                tempUsedInputs.add(utxo);
            }
            visited[u] = true;
            if (isLeaf[u]) return value[u];
            double res = value[u];
            for (Integer v : a.get(u)) {
                for (Integer id : edgeIndexes.get(u).get(v)) {
                    if (usedIndex.get(v).contains(id)) {
                        return -1;
                    }
                    usedIndex.get(v).add(id);
                }

                if (!visited[v]) {
                    double cur = dfsGetValue(v);
                    if (cur < 0) return -1;
                    res += cur;
                }
            }
            return res;
        }

        private double getTreeValue(int root) {
            // return -1 if is not a tree
            for (int i = 0; i < n; ++i) visited[i] = false;
            tempUsedInputs.clear();
            for (int i = 0; i < n; ++i) {
                usedIndex.get(i).clear();
            }
            return dfsGetValue(root);
        }

        private boolean dfsCheckUnused(int u) {
            for (Integer v : a.get(u)) {
                if (used[v]) {
                    for (Integer id : edgeIndexes.get(u).get(v)) {
                        if (usedIndex.get(v).contains(id)) {
                            return false;
                        }
                    }
                    continue;
                }
                if (!dfsCheckUnused(v)) return false;
            }
            return true;
        }

        private void dfsGrab(int u, ArrayList<Integer> result) {
            used[u] = true;
            for (Integer v : a.get(u)) {
                for (Integer id : edgeIndexes.get(u).get(v)) {
                    usedIndex.get(v).add(id);
                }
                if (used[v]) continue;
                dfsGrab(v, result);
            }
            result.add(u);
        }

        public ArrayList<Integer> getMaxDisjointTrees() {
            ArrayList<Integer> roots = new ArrayList<>();
            double[] treeValue = new double[n];
            for (int i = 0; i < n; ++i) {
                treeValue[i] = getTreeValue(i);
                if (treeValue[i] > 0) {
                    roots.add(i);
                }
            }
            
            // sort by values from largest to smallest
            roots.sort(new Comparator<Integer>() {
                 @Override
                public int compare(Integer u, Integer v) {
                    return -Double.compare(treeValue[u], treeValue[v]);
                }
            });

            for (int i = 0; i < n; ++i) {
                usedIndex.get(i).clear();
            }

            ArrayList<Integer> result = new ArrayList<>();
            for (int i = 0; i < roots.size(); ++i) if (!used[roots.get(i)]) {
                if (dfsCheckUnused(roots.get(i))) {
                    dfsGrab(roots.get(i), result);
                }
            }

            return result;
        }

        public void addEdge(int u, int v, ArrayList<Integer> indexes) {
            a.get(u).add(v);
            if (!edgeIndexes.containsKey(u)) {
                edgeIndexes.put(u, new HashMap<>());
            }
            if (!edgeIndexes.get(u).containsKey(v)) {
                edgeIndexes.get(u).put(v, new HashSet<>());
            }
            for (Integer id : indexes) {
                edgeIndexes.get(u).get(v).add(id);
            }
        }

        public void setValue(int i, double v) {
            value[i] = v;
        }

        public void setLeaf(int u) {
            isLeaf[u] = true;
        }
    }

    ArrayList<Integer> linked(Transaction a, Transaction b) {
        // does b uses a's outputs?
        ArrayList<Integer> res = new ArrayList<Integer>();
        for (int i = 0; i < b.numInputs(); ++i) {
            Transaction.Input currentInput = b.getInput(i);
            if (Arrays.equals(currentInput.prevTxHash, a.getHash())) {
                res.add(currentInput.outputIndex);
            }
        }
        return res;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) return null;
        // first forget about the transactions not respecting conditions 2-5
        // create dependencies of from: if transaction A is accepted, then be must be accepted before A
        // note that dependencies act on OuputIndex level, 2 transactions depending on 1 nodes with different output indexes are both VALID
        // a good set of transactions must have its node's dependencies form a forest (a set of disjoint trees)
        // => algorithm:
        //      sort the trees (each uniquely determined by its root - the last transaction) descendingly by fee
        //      then just greedily collect trees: pick the greatest, update unspentPool, then repeat

        // Create tempPool containing all outputs of possibleTxs[]
        for (UTXO utxo : tempPool.getAllUTXO()) tempPool.removeUTXO(utxo);
        for (int id = 0; id < possibleTxs.length; ++id) {
            for (int i = 0; i < possibleTxs[id].numOutputs(); ++i) {
                Transaction.Output currentOutput = possibleTxs[id].getOutput(i);
                UTXO utxo = new UTXO(possibleTxs[id].getHash(), i);
                tempPool.addUTXO(utxo, currentOutput);
            }
        }

        // A transaction is good iff it respect conditions 2-5 AND its inputs are within unspentPool or tempPool
        ArrayList<Transaction> goodTransactions = new ArrayList<>();
        for (int i = 0; i < possibleTxs.length; ++i) {
            if (isValidTx(possibleTxs[i], true)) {
                goodTransactions.add(possibleTxs[i]);
            }
        }

        // Build the dependency graph
        Graph graph = new Graph(goodTransactions.size());

        for (int id = 0; id < goodTransactions.size(); ++id) {
            Transaction tx = goodTransactions.get(id);
            for (int i = 0; i < tx.numInputs(); ++i) {
                Transaction.Input currentInput = tx.getInput(i);
                UTXO utxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);
                if (unspentPool.contains(utxo)) {
                    graph.addUsedInput(id, utxo);
                }
            }
        }

        for (int i = 0; i < goodTransactions.size(); ++i) {
            for (int j = 0; j < goodTransactions.size(); ++j) {
                ArrayList<Integer> e = linked(goodTransactions.get(j), goodTransactions.get(i));
                if (e.size() > 0) {
                    graph.addEdge(i, j, e);
                }
            }
        }

        for (int i = 0; i < goodTransactions.size(); ++i) {
            graph.setValue(i, getFee(goodTransactions.get(i)));
            if (isValidTx(goodTransactions.get(i))) {
                graph.setLeaf(i);
            }
        }

        // Collect result & update unspentPool accordingly

        ArrayList<Integer> best = graph.getMaxDisjointTrees();
        ArrayList<Transaction> result = new ArrayList<>();

        for (int id = 0; id < best.size(); ++id) {
            Transaction tx = goodTransactions.get(best.get(id));
            if (isValidTx(tx)) {
                result.add(tx);
                // remove tx's input from unspentPool
                for (int i = 0; i < tx.numInputs(); ++i) {
                    Transaction.Input currentInput = tx.getInput(i);
                    UTXO utxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);
                    unspentPool.removeUTXO(utxo);
                }

                // add tx's output to unspentPool
                for (int i = 0; i < tx.numOutputs(); ++i) {
                    Transaction.Output currentOutput = tx.getOutput(i);
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    unspentPool.addUTXO(utxo, currentOutput);
                }
            }
        }

        Transaction[] resultArray = new Transaction[result.size()];
        result.toArray(resultArray);
        return resultArray;
    }
}
