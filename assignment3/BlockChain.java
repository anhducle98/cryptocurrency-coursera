// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    private class ActiveBlock implements Comparable<ActiveBlock> {
        public Block block;
        public UTXOPool utxoPool;
        public int height;
        public int creationTime;

        ActiveBlock(Block block, UTXOPool utxoPool, int height, int creationTime) {
            this.block = block;
            this.utxoPool = utxoPool;
            this.height = height;
            this.creationTime = creationTime;
        }

        public int compareTo(ActiveBlock other) {
            if (this.height != other.height) return Integer.compare(this.height, other.height);
            return -Integer.compare(this.creationTime, other.creationTime);
        }
    }

    TreeSet<ActiveBlock> activeBlocks;
    HashMap<ByteArrayWrapper, ActiveBlock> hash2ActiveBlock;
    TransactionPool pending;
    private int timestamp;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        UTXOPool genesisUTXOPool = new UTXOPool();
        for (Transaction tx : genesisBlock.getTransactions()) {
            addUTXONewTransaction(genesisUTXOPool, tx);
        }
        addUTXONewTransaction(genesisUTXOPool, genesisBlock.getCoinbase());
        ActiveBlock genesisActiveBlock = new ActiveBlock(genesisBlock, genesisUTXOPool, 0, 0);
        activeBlocks = new TreeSet<>();
        activeBlocks.add(genesisActiveBlock);
        hash2ActiveBlock = new HashMap<>();
        hash2ActiveBlock.put(new ByteArrayWrapper(genesisBlock.getHash()), genesisActiveBlock);
        pending = new TransactionPool();
        timestamp = 0;
    }

    private void addUTXONewTransaction(UTXOPool utxoPool, Transaction tx) {
        int numOutputs = tx.getOutputs().size();
        for (int i = 0; i < numOutputs; ++i) {
            UTXO utxo = new UTXO(tx.getHash(), i);
            utxoPool.addUTXO(utxo, tx.getOutput(i));
        }
    }

    private void trim() {
        int maxHeight = activeBlocks.last().height;
        while (activeBlocks.first().height < maxHeight - CUT_OFF_AGE) {
            ActiveBlock tobeForgotten = activeBlocks.pollFirst();
            hash2ActiveBlock.remove(new ByteArrayWrapper(tobeForgotten.block.getHash()));
        }
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return activeBlocks.last().block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return activeBlocks.last().utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return pending;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] parentHash = block.getPrevBlockHash();
        if (parentHash == null) return false;
        ByteArrayWrapper parentHashWrapper = new ByteArrayWrapper(parentHash);
        if (!hash2ActiveBlock.containsKey(parentHashWrapper)) return false;

        ActiveBlock parentBlock = hash2ActiveBlock.get(parentHashWrapper);

        TxHandler txHandler = new TxHandler(parentBlock.utxoPool);
        int validTxCount = 0;
        while (true) {
            int found = 0;
            for (Transaction tx : block.getTransactions()) {
                if (!txHandler.isValidTx(tx)) continue;
                found++;
                for (Transaction.Input input : tx.getInputs()) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    txHandler.getUTXOPool().removeUTXO(utxo);
                }
                addUTXONewTransaction(txHandler.getUTXOPool(), tx);
            }
            if (found == 0) break;
            validTxCount += found;
            if (validTxCount >= block.getTransactions().size()) break;
        }

        if (validTxCount < block.getTransactions().size()) return false;
        addUTXONewTransaction(txHandler.getUTXOPool(), block.getCoinbase());

        ActiveBlock currentBlock = new ActiveBlock(block, txHandler.getUTXOPool(), parentBlock.height + 1, ++timestamp);
        activeBlocks.add(currentBlock);
        hash2ActiveBlock.put(new ByteArrayWrapper(currentBlock.block.getHash()), currentBlock);

        for (Transaction tx : block.getTransactions()) {
            pending.removeTransaction(tx.getHash());
        }

        trim();

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        pending.addTransaction(tx);
    }
}