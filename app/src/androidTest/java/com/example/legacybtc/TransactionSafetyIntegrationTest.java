package com.example.legacybtc;

import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Context;
import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.*;
import org.junit.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.*;

public class TransactionSafetyIntegrationTest {
    private static class Ready extends BitcoinNetworkManager {
        int broadcasts;
        @Override public void stop() { }
        @Override public boolean isReady() { return true; }
        @Override public void start(Context c,Wallet w,String f) { }
        @Override public CompletableFuture<Transaction> broadcast(Transaction tx) { broadcasts++; return CompletableFuture.completedFuture(tx); }
    }
    @Test public void staleOrModifiedReviewsCannotSignAndReconnectNeverRelays() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext(); new org.bitcoinj.core.Context();
        Wallet wallet=WalletPolicy.createWallet(); Transaction income=new Transaction();
        income.addInput(Sha256Hash.of(new byte[]{65}),0,ScriptBuilder.createEmpty()); income.addOutput(Coin.COIN,wallet.currentReceiveAddress()); wallet.commitTx(income);
        Block genesis=ElectrumHeaderStore.parameters().getGenesisBlock();
        wallet.receiveFromBlock(income,new StoredBlock(genesis,genesis.getWork(),100),AbstractBlockChain.NewBlockType.BEST_CHAIN,0);
        income.getConfidence().setDepthInBlocks(6);
        String file=WalletStorageManager.saveWallet(context,wallet), other=WalletStorageManager.saveWallet(context,WalletPolicy.createWallet());
        Ready network=new Ready(); BitcoinService service=new BitcoinService(context,network);
        String recipient=WalletPolicy.createWallet().currentReceiveAddress().toString();
        try {
            service.submit(()->service.openWallet(file)).get();
            SendRequest stale=service.submit(()->service.preparePayment(recipient,"0.01","1")).get();
            service.submit(()->service.openWallet(other)).get();
            assertThrows(java.util.concurrent.ExecutionException.class,()->service.submit(()->service.sendBitcoin(stale)).get());
            service.submit(()->service.openWallet(file)).get();
            SendRequest changed=service.submit(()->service.preparePayment(recipient,"0.01","1")).get();
            changed.tx.replaceOutput(0,changed.tx.getOutput(0).withValue(Coin.CENT.add(Coin.SATOSHI)));
            assertThrows(java.util.concurrent.ExecutionException.class,()->service.submit(()->service.sendBitcoin(changed)).get());
            assertEquals(Coin.COIN,service.getBalance());
            SendRequest review=service.submit(()->service.preparePayment(recipient,"0.01","1")).get();
            Transaction saved=service.submit(()->service.sendBitcoin(review)).get();
            assertNotNull(WalletStorageManager.loadWallet(context,file).getTransaction(saved.getTxId()));
            service.submit(()->{service.reconnect();return true;}).get(); assertEquals(0,network.broadcasts);
            service.submit(()->service.broadcast(saved)).get().get(); assertEquals(1,network.broadcasts);
            assertThrows(java.util.concurrent.ExecutionException.class,()->service.submit(()->service.sendBitcoin(review)).get());
        } finally {
            service.submit(()->{service.shutdown();return true;}).get();
            WalletStorageManager.deleteWallet(context,file); WalletStorageManager.deleteWallet(context,other);
        }
    }
}
