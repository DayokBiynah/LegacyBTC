package com.example.legacybtc;

import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.base.Coin;
import java.util.ArrayList;

/**
 * I associate an encrypted storage filename with the opened bitcoinj Wallet and expose public
 * address, spendable balance, and transaction history. The constructor enforces filename and
 * network isolation. I loop over bitcoinj's real transactions to create ArrayList history
 * records rather than inventing a separate payment database. Sensitive mutations remain in
 * BitcoinService.
 */
public final class BitcoinWallet {
    private final String fileName;
    private final Wallet wallet;
    public BitcoinWallet(String fileName, Wallet wallet) {
        WalletStorageManager.validateFileName(fileName);
        WalletPolicy.requireNetwork(wallet);
        this.fileName = fileName;
        this.wallet = wallet;
    }
    public String getFileName() { return fileName; }
    public Wallet getWallet() { return wallet; }
    public String getAddress() { return wallet.currentReceiveAddress().toString(); }
    /**
     * I return the amount bitcoinj currently selects for ordinary payments as an exact Coin
     * value. Watched inheritance outputs are excluded from payment candidates. Confirmed and
     * unconfirmed output totals are displayed separately because confirmation category and
     * spendability are different questions. The interface formats this result as BTC and
     * preparation checks it before selecting inputs.
     * @return the spendable ordinary-payment amount as an exact Coin
     */
    public Coin getBalance() { return WalletPolicy.getBalance(wallet); }
    /**
     * I retrieve the opened wallet's actual bitcoinj transactions and expose an ArrayList of
     * public BitcoinTransaction records. The wallet wrapper loops through recent transactions,
     * including confidence changes; the service delegates to that wrapper. The History and
     * Dashboard screens use these records rather than simulated balances or a second database.
     * @return an ArrayList of actual wallet transaction history records
     */
    public ArrayList<BitcoinTransaction> getTransactions() {
        ArrayList<BitcoinTransaction> records = new ArrayList<>();
        for (org.bitcoinj.core.Transaction tx : wallet.getRecentTransactions(0, true))
            records.add(new BitcoinTransaction(tx, wallet));
        return records;
    }
}
