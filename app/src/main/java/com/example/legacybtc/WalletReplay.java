package com.example.legacybtc;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Wallet;
import java.util.ArrayList;

/**
 * I rebuild confirmed transaction history when newly watched inheritance outputs may predate
 * the saved chain. I retain pending transactions in an ArrayList, call Wallet.reset to reset
 * chain-derived history, then reinsert those pending objects. Deterministic keys remain
 * available. The service persists the replay state before restarting synchronization.
 */
public final class WalletReplay {
    private WalletReplay() { }
    /**
     * I collect pending transactions before Wallet.reset rebuilds chain-derived history, then
     * loop over that collection and call maybeCommitTx to retain uncertain submissions. This
     * avoids turning a relay retry into a new payment. Deterministic keys are kept; replay
     * will rediscover confirmed transactions.
     * @param wallet Wallet supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public static void resetForReplay(Wallet wallet) throws Exception {
        ArrayList<Transaction> pending = new ArrayList<>();
        for (Transaction tx : wallet.getTransactions(true)) {
            if (tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) pending.add(tx);
        }
        wallet.reset();
        for (Transaction tx : pending) wallet.maybeCommitTx(tx);
    }
}
