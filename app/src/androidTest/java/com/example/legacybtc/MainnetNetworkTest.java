package com.example.legacybtc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.bitcoinj.wallet.Wallet;
import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

/**
 * I test live Mainnet discovery only when explicitly selected with mainnetSmoke=true.
 * The fixture wallet has fresh keys and no funds. I never construct, sign, or broadcast
 * a transaction. Connected peers prove discovery/handshake only, not complete replay.
 */
public class MainnetNetworkTest {
    @Test public void discoversMainnetPeersWithoutBroadcasting() throws Exception {
        Assume.assumeTrue(BuildConfig.MAINNET && Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("mainnetSmoke", "false")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Wallet wallet = WalletPolicy.createWallet();
        String file = "legacybtc_wallet_mainnet_smoke-" + UUID.randomUUID() + ".enc";
        BitcoinNetworkManager manager = new BitcoinNetworkManager();
        try {
            manager.start(context, wallet, file);
            long deadline = android.os.SystemClock.elapsedRealtime() + 60_000;
            while (manager.getConnectedPeerCount() == 0 && android.os.SystemClock.elapsedRealtime() < deadline)
                android.os.SystemClock.sleep(250);
            assertTrue("No Mainnet bloom-filter peer connected within 60 seconds: " + manager.getStatus(), manager.getConnectedPeerCount() > 0);
            assertTrue(wallet.getTransactions(true).isEmpty());
        } finally {
            manager.stop();
            // This exact UUID chain fixture belongs to this test; no existing wallet is removed.
            File chain = new File(context.getFilesDir(), file + ".spvchain");
            if (chain.exists()) assertTrue(chain.delete());
        }
    }
}
