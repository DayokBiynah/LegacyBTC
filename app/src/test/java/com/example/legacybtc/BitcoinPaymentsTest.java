package com.example.legacybtc;

import org.junit.Test;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import static org.junit.Assert.*;

/** Synthetic transactions test construction and signatures without contacting a network. */
public class BitcoinPaymentsTest {
    private Wallet fundedWallet() throws Exception {
        new org.bitcoinj.core.Context();
        Wallet wallet = WalletPolicy.createWallet();
        Transaction funding = new Transaction();
        funding.addInput(Sha256Hash.of(new byte[]{1, 2, 3}), 0, ScriptBuilder.createEmpty());
        funding.addOutput(Coin.COIN, wallet.currentReceiveAddress());
        wallet.commitTx(funding);
        // Mark the synthetic funding as confirmed only inside this offline test.
        funding.getConfidence().setConfidenceType(org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING);
        funding.getConfidence().setDepthInBlocks(6);
        return wallet;
    }
    @Test public void previewIncludesFeeAndSigningProducesValidInputs() throws Exception {
        Wallet wallet = fundedWallet();
        Wallet recipient = WalletPolicy.createWallet();
        SendRequest request = BitcoinPayments.prepare(wallet, recipient.currentReceiveAddress().toString(), "0.01", "5");
        assertTrue(request.tx.getFee().isPositive());
        assertEquals(Coin.COIN, request.tx.getInputSum());
        request.signInputs = true; wallet.signTransaction(request);
        for (org.bitcoinj.core.TransactionInput input : request.tx.getInputs()) input.verify();
        wallet.commitTx(request.tx);
        assertEquals(2, wallet.getTransactionsByTime().size());
        assertTrue(new BitcoinTransaction(request.tx, wallet).getAmount().isNegative());
    }
    @Test public void paymentCannotSpendBalanceReservedForFees() throws Exception {
        Wallet wallet = fundedWallet();
        String recipient = WalletPolicy.createWallet().currentReceiveAddress().toString();
        assertThrows(org.bitcoinj.core.InsufficientMoneyException.class, () -> BitcoinPayments.prepare(wallet, recipient, "1", "5"));
        assertThrows(IllegalArgumentException.class, () -> BitcoinPayments.prepare(wallet, recipient, "2", "5"));
        assertThrows(Exception.class, () -> BitcoinPayments.prepare(wallet, recipient, "0.00000001", "5"));
    }
    @Test public void balanceCategoriesTrackConfirmationAndExcludeSpentOutputs() throws Exception {
        Wallet wallet = fundedWallet();
        assertEquals(Coin.COIN, WalletPolicy.getOutputBalance(wallet, true));
        assertEquals(Coin.ZERO, WalletPolicy.getOutputBalance(wallet, false));
        Transaction pending = new Transaction();
        pending.addInput(Sha256Hash.of(new byte[]{9, 8, 7}), 0, ScriptBuilder.createEmpty());
        pending.addOutput(Coin.CENT, wallet.currentReceiveAddress());
        wallet.commitTx(pending);
        assertEquals(Coin.CENT, WalletPolicy.getOutputBalance(wallet, false));
        assertEquals(Coin.COIN, WalletPolicy.getOutputBalance(wallet, true));
    }
    /** I ensure a permitted rate cannot bypass the absolute fee cap on a many-input spend. */
    @Test public void manyInputPaymentCannotExceedAbsoluteFeeSafetyLimit() throws Exception {
        new org.bitcoinj.core.Context();
        Wallet wallet = WalletPolicy.createWallet();
        for (int index = 0; index < 20; index++) {
            Transaction income = new Transaction();
            income.addInput(Sha256Hash.of(new byte[]{(byte) index, 42}), 0, ScriptBuilder.createEmpty());
            income.addOutput(Coin.valueOf(5_000_000), wallet.currentReceiveAddress());
            wallet.commitTx(income);
            income.getConfidence().setConfidenceType(org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING);
            income.getConfidence().setDepthInBlocks(6);
        }
        String recipient = WalletPolicy.createWallet().currentReceiveAddress().toString();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> BitcoinPayments.prepare(wallet, recipient, "0.9", "1000"));
        assertTrue(failure.getMessage().contains("safety limit"));
        assertEquals(20, wallet.getTransactions(true).size());
    }
}
