package com.example.legacybtc;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;
import static org.junit.Assert.*;

/** I test externally decoded QR/URI inputs offline; none of these tests submit payments. */
public class PaymentInputTest {
    @Test public void uriAmountIsExactAndConflictingManualAmountIsRejected() throws Exception {
        String address = WalletPolicy.createWallet().currentReceiveAddress().toString();
        PaymentInput input = PaymentInput.parse("bitcoin:" + address + "?amount=0.00000001", "");
        assertEquals(address, input.getAddress());
        assertEquals("0.00000001", input.getAmount());
        assertThrows(IllegalArgumentException.class, () -> PaymentInput.parse("bitcoin:" + address + "?amount=0.01", "0.02"));
        assertThrows(Exception.class, () -> PaymentInput.parse("bitcoin:" + address + "?req-unknown=1", "0.01"));
    }
    @Test public void uriCannotBypassNetworkOrPositiveAmountRules() {
        String other = Wallet.createDeterministic(BuildConfig.MAINNET ? BitcoinNetwork.TESTNET : BitcoinNetwork.MAINNET,
                ScriptType.P2WPKH).currentReceiveAddress().toString();
        assertThrows(Exception.class, () -> PaymentInput.parse("bitcoin:" + other, "0.01"));
        String own = WalletPolicy.createWallet().currentReceiveAddress().toString();
        assertThrows(Exception.class, () -> PaymentInput.parse("bitcoin:" + own + "?amount=0", ""));
        assertThrows(Exception.class, () -> PaymentInput.parse("bitcoin:" + own, ""));
    }
}
