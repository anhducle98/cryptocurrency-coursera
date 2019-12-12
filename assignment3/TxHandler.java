import java.util.HashSet;
import java.util.ArrayList;

public class TxHandler {

    private UTXOPool unspentPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        unspentPool = new UTXOPool(utxoPool);
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
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS

        double balance = 0;
        HashSet<UTXO> used = new HashSet<>();

        for (int i = 0; i < tx.numInputs(); ++i) {
            Transaction.Input currentInput = tx.getInput(i);
            UTXO utxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);

            if (!unspentPool.contains(utxo)) return false; // condition (1)

            Transaction.Output previousOutput = unspentPool.getTxOutput(utxo);
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

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS

        ArrayList<Transaction> acceptedTransactions = new ArrayList<>();

        while (true) {
            boolean foundSomething = false;
            for (Transaction tx : possibleTxs) {
                if (isValidTx(tx)) {
                    acceptedTransactions.add(tx);
                    foundSomething = true;
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
            if (!foundSomething) break;
        }

        Transaction[] result = new Transaction[acceptedTransactions.size()];
        acceptedTransactions.toArray(result);
        return result;
    }

    public UTXOPool getUTXOPool() {
        return unspentPool;
    }
}
