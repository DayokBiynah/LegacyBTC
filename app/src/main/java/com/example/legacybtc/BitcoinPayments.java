package com.example.legacybtc;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.SendRequest;

/** Payment construction is shared by the UI and tests, while networking stays in BitcoinService. */
public final class BitcoinPayments {
    private BitcoinPayments() { }
    /**
     * This method receives the opened bitcoinj Wallet and three Strings from the Send
     * screen: recipient address, BTC amount, and satoshis per virtual byte. I validate
     * the network and values before SendRequest.to creates the destination output.
     * setFeePerVkb receives satoshis per 1,000 virtual bytes, so I multiply the rate by
     * 1,000. Wallet.completeTx selects spendable UTXOs, adds inputs and change, and
     * calculates the fee. Setting signInputs=false leaves authorization for the review
     * step. The returned request supplies the exact fee/total shown in the dialog.
     * InsufficientMoneyException also covers a balance that cannot pay the fee; bitcoinj
     * can reject dust outputs. Neither failure commits or broadcasts a transaction.
     * @param wallet currently opened wallet
     * @param recipient destination address text
     * @param amount exact decimal BTC amount
     * @param feeRate integer satoshis per virtual byte
     * @return completed unsigned request for user review
     * @throws Exception when validation, UTXO selection, or fee calculation fails
     */
    public static SendRequest prepare(Wallet wallet, String recipient, String amount, String feeRate) throws Exception {
        WalletPolicy.requireNetwork(wallet);
        Address destination = WalletPolicy.validateAddress(recipient);
        Coin value = WalletPolicy.parseAmount(amount);
        if (value.isGreaterThan(WalletPolicy.getBalance(wallet)))
            throw new IllegalArgumentException("Insufficient available balance.");
        SendRequest request = SendRequest.to(destination, value);
        request.setFeePerVkb(Coin.valueOf(WalletPolicy.parseFeeRate(feeRate) * 1000));
        request.signInputs = false;
        wallet.completeTx(request);
        if (request.tx.getFee() == null || request.tx.getFee().isGreaterThan(WalletPolicy.MAX_TRANSACTION_FEE))
            throw new IllegalArgumentException("Fee exceeds the 0.01 BTC safety limit. Reduce the rate or input count.");
        return request;
    }
}
