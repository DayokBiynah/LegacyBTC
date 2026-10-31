package com.example.legacybtc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.bitcoinj.wallet.Wallet;
import org.junit.Assume;
import org.junit.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Read-only end-to-end bootstrap and wallet switching, enabled explicitly on an isolated install. */
public class ElectrumBackendSmokeTest {
    @Test public void verifiedEmptyWalletsReuseSharedHeadersWithoutSpvFiles() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("electrumBackendSmoke","false")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Use -PisolatedWalletTests=true",BuildConfig.APPLICATION_ID.endsWith(".verification"));
        BitcoinService service=new BitcoinService(context,new BitcoinNetworkManager());
        String a=WalletStorageManager.saveWallet(context,WalletPolicy.createWallet()), b=WalletStorageManager.saveWallet(context,WalletPolicy.createWallet());
        try {
            service.submit(()->{service.openWallet(a);service.connect();return true;}).get(20,TimeUnit.SECONDS);
            waitReady(service,15*60);
            int height=service.requireWallet().getWallet().getLastBlockSeenHeight();
            long switched=android.os.SystemClock.elapsedRealtime();
            service.submit(()->{service.openWallet(b);service.connect();return true;}).get(20,TimeUnit.SECONDS);
            waitReady(service,120);
            assertTrue(service.requireWallet().getWallet().getLastBlockSeenHeight()>=height);
            System.out.println("Verified wallet switch in "+(android.os.SystemClock.elapsedRealtime()-switched)+" ms, shared height "+height);
            assertTrue(service.requireWallet().getWallet().getTransactions(true).isEmpty());
            assertFalse(new java.io.File(context.getFilesDir(),a+".spvchain").exists());
            assertFalse(new java.io.File(context.getFilesDir(),b+".spvchain").exists());
        } finally {
            service.submit(()->{service.shutdown();return true;}).get(30,TimeUnit.SECONDS);
            WalletStorageManager.deleteWallet(context,a); WalletStorageManager.deleteWallet(context,b);
        }
    }
    private void waitReady(BitcoinService service,int seconds) throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L, nextLog=0;
        while(!service.getNetwork().isReady() && android.os.SystemClock.elapsedRealtime()<deadline) {
            if(android.os.SystemClock.elapsedRealtime()>nextLog) { System.out.println(service.getStatus()); nextLog=android.os.SystemClock.elapsedRealtime()+10000; }
            Thread.sleep(1000);
        }
        assertTrue(service.getStatus(),service.getNetwork().isReady());
    }
}
