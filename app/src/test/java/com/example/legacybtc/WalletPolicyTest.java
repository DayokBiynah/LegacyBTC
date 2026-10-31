package com.example.legacybtc;
import org.junit.Test;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.wallet.Wallet;
import java.util.Locale;
import static org.junit.Assert.*;

public class WalletPolicyTest {
    // Public BIP-39 vector; never use this phrase for funds.
    private static final String PHRASE = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
    @Test public void recoveryPreservesAccountAcrossLocalesAndAddressChanges() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            Wallet a = WalletPolicy.restoreWallet(PHRASE.toUpperCase(Locale.ROOT), "");
            Wallet b = WalletPolicy.restoreWallet("  " + PHRASE.replace(" ", "\n  ") + "  ", "");
            assertEquals(a.currentReceiveAddress(), b.currentReceiveAddress());
            String identity = WalletPolicy.walletIdentity(a);
            a.freshReceiveAddress();
            assertEquals(identity, WalletPolicy.walletIdentity(a));
            assertEquals(identity, WalletPolicy.walletIdentity(b));
        } finally { Locale.setDefault(original); }
    }
    @Test public void invalidChecksumAndOtherNetworkAreRejected() {
        assertThrows(Exception.class, () -> WalletPolicy.restoreWallet(PHRASE.replace("about", "abandon"), ""));
        Wallet other = Wallet.createDeterministic(BuildConfig.MAINNET ? BitcoinNetwork.TESTNET : BitcoinNetwork.MAINNET, ScriptType.P2WPKH);
        assertThrows(IllegalArgumentException.class, () -> WalletPolicy.validateAddress(other.currentReceiveAddress().toString()));
        assertThrows(IllegalArgumentException.class, () -> WalletPolicy.requireNetwork(other));
    }
    @Test public void validatesSatoshisAndFeeRates() {
        assertEquals(1, WalletPolicy.parseAmount("0.00000001").value);
        for (String value : new String[]{"0", "-1", "NaN", "1e-8", "0.000000001", "21000001"})
            assertThrows(IllegalArgumentException.class, () -> WalletPolicy.parseAmount(value));
        assertThrows(IllegalArgumentException.class, () -> WalletPolicy.parseFeeRate("0"));
        assertThrows(IllegalArgumentException.class, () -> WalletPolicy.parseFeeRate("1001"));
        assertEquals(5, WalletPolicy.parseFeeRate("5"));
    }
}
