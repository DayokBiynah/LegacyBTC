package com.example.legacybtc;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;
import static org.junit.Assert.*;

/** exercises the same isolation rules in both Testnet and Mainnet builds without peers. */
public class NetworkIsolationTest {
    @Test public void networkAddressAndFilenameAgreeWithBuild() {
        Wallet wallet = WalletPolicy.createWallet();
        assertEquals(BuildConfig.REGTEST ? BitcoinNetwork.REGTEST : BuildConfig.MAINNET ? BitcoinNetwork.MAINNET : BitcoinNetwork.TESTNET, wallet.network());
        assertEquals(wallet.currentReceiveAddress(), WalletPolicy.validateAddress(wallet.currentReceiveAddress().toString()));
        String own = BuildConfig.REGTEST ? "legacybtc_wallet_regtest_test.enc" : BuildConfig.MAINNET ? "legacybtc_wallet_mainnet_test.enc" : "legacybtc_wallet_test.enc";
        String other = BuildConfig.MAINNET ? "legacybtc_wallet_test.enc" : "legacybtc_wallet_mainnet_test.enc";
        WalletStorageManager.validateFileName(own);
        assertThrows(IllegalArgumentException.class, () -> WalletStorageManager.validateFileName(other));
        Wallet different = Wallet.createDeterministic(BuildConfig.MAINNET ? BitcoinNetwork.TESTNET : BitcoinNetwork.MAINNET, ScriptType.P2WPKH);
        assertThrows(IllegalArgumentException.class, () -> WalletPolicy.requireNetwork(different));
        assertThrows(IllegalArgumentException.class, () -> WalletPolicy.validateAddress(different.currentReceiveAddress().toString()));
        assertEquals(BuildConfig.REGTEST ? 51002 : BuildConfig.MAINNET ? 50002 : 60002, WalletPolicy.DEFAULT_PORT);
    }
}
