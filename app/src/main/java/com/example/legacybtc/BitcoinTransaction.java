package com.example.legacybtc;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.base.Coin;
import java.util.Date;

/**
 * I convert a bitcoinj Transaction into a public history record for the XML list. The wallet-
 * relative Coin value is negative for money sent and positive for money received; it may
 * include fees and internal transfers. TransactionConfidence supplies pending, confirmed,
 * conflicting, or invalidated status. The transaction ID identifies the serialized
 * transaction, not proof of relay or mining. Date getters return defensive copies.
 */
public final class BitcoinTransaction {
    private final String id, type, status;
    private final Coin amount;
    private final Date date;
    public BitcoinTransaction(Transaction tx, Wallet wallet) {
        // I receive an actual wallet transaction, calculate its value relative to this wallet,
        // and snapshot public fields. This avoids exposing ECKey or mnemonic data to row views.
        id = tx.getTxId().toString();
        amount = tx.getValue(wallet);
        String memo = tx.getMemo();
        if (memo != null && memo.startsWith("LegacyBTC inheritance ")) type = memo;
        else type = amount.isNegative() ? "Sent" : amount.isPositive() ? "Received" : "Transfer";
        TransactionConfidence confidence = tx.getConfidence();
        // Selection follows bitcoinj's confidence rather than assuming broadcast means mining.
        // BUILDING depth can decrease after a reorganization; future refresh creates a new record.
        switch (confidence.getConfidenceType()) {
            case BUILDING: status = "Confirmed • " + confidence.getDepthInBlocks() + " confirmations"; break;
            case DEAD: status = "Invalidated / double spent"; break;
            case IN_CONFLICT: status = "Conflicting transaction"; break;
            case PENDING: status = "Pending confirmation"; break;
            default: status = "Unverified";
        }
        date = tx.getUpdateTime();
    }
    public String getId() { return id; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public Coin getAmount() { return amount; }
    public Date getDate() { return new Date(date.getTime()); }
}
