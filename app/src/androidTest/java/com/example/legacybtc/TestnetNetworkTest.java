package com.example.legacybtc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

/** Opt-in live connectivity check. It creates no payments and never requests or spends coins. */
@RunWith(AndroidJUnit4.class)
public class TestnetNetworkTest {
    @Test public void discoversTestnetPeers() throws Exception {
        Assume.assumeFalse("Live smoke test is Testnet-only", BuildConfig.MAINNET);
        Assume.assumeTrue("Enable with testnetSmoke=true", Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("testnetSmoke", "false")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new org.bitcoinj.core.Context();
        BitcoinNetworkManager network = new BitcoinNetworkManager();
        String file = "legacybtc_wallet_" + UUID.randomUUID() + ".enc";
        try {
            network.start(context, WalletPolicy.createWallet(), file);
            long deadline = android.os.SystemClock.elapsedRealtime() + 60_000;
            while (network.getConnectedPeerCount() == 0 && android.os.SystemClock.elapsedRealtime() < deadline)
                Thread.sleep(1000);
            System.out.println("Live Testnet peers: " + network.getConnectedPeerCount() + "; " + network.getStatus());
            assertTrue("No Testnet bloom-filter peer connected within 60 seconds", network.getConnectedPeerCount() > 0);
        } finally { network.stop(); context.deleteFile(file + ".spvchain"); }
    }
}
