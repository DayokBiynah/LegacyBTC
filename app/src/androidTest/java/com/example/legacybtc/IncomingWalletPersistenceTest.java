package com.example.legacybtc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.*;
import org.bitcoinj.script.ScriptBuilder;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Disposable fixtures only; uses the isolated instrumentation application and its Keystore. */
public class IncomingWalletPersistenceTest {
    static final class Controlled extends BitcoinNetworkManager {
        WalletAccess access;
        boolean verified;
        @Override void setWalletAccess(WalletAccess access) { this.access = access; }
        @Override public void start(Context c, Wallet w, String f) { }
        @Override public void stop() { verified = false; }
        @Override public boolean isReady() { return verified; }
    }
    @Test public void confirmedReceiveSurvivesEncryptedReopenSwitchesAndOutage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new org.bitcoinj.core.Context();
        Wallet a = WalletPolicy.createWallet(), b = WalletPolicy.createWallet();
        String address = b.currentReceiveAddress().toString();
        org.bitcoinj.crypto.ECKey observer = new org.bitcoinj.crypto.ECKey();
        b.addWatchedAddress(observer.toAddress(ScriptType.P2WPKH,WalletPolicy.NETWORK));
        Transaction incoming = new Transaction();
        incoming.addInput(Sha256Hash.of(new byte[]{41,42}),0,ScriptBuilder.createEmpty());
        incoming.addOutput(Coin.CENT,b.currentReceiveAddress());
        String id = incoming.getTxId().toString(), hash = ElectrumClient.scriptHash(b.currentReceiveAddress());
        ElectrumRpc server = new ElectrumRpc() {
            public Object request(String method, JSONArray p) throws Exception {
                boolean relevant = hash.equals(p.getString(0));
                switch (method) {
                    case "blockchain.scripthash.get_history": return relevant ? new JSONArray().put(new JSONObject().put("tx_hash",id).put("height",1)) : new JSONArray();
                    case "blockchain.scripthash.listunspent": return relevant ? new JSONArray().put(new JSONObject().put("tx_hash",id).put("height",1).put("tx_pos",0).put("value",Coin.CENT.value)) : new JSONArray();
                    case "blockchain.scripthash.get_balance": return new JSONObject().put("confirmed",relevant ? Coin.CENT.value : 0).put("unconfirmed",0);
                    case "blockchain.transaction.get": return Hex.encode(incoming.serialize());
                    case "blockchain.transaction.get_merkle": return new JSONObject().put("block_height",1).put("pos",0).put("merkle",new JSONArray());
                    default: throw new AssertionError(method);
                }
            }
            public List<Object> batch(String m,List<JSONArray> p) throws Exception { List<Object> r=new ArrayList<>(); for(JSONArray v:p) r.add(request(m,v)); return r; }
            public void close() { }
        };
        ElectrumSync.Headers headers = new ElectrumSync.Headers() {
            public StoredBlock at(int h) { return new StoredBlock(new Block(1,Sha256Hash.ZERO_HASH,incoming.getTxId(),java.time.Instant.ofEpochSecond(1_800_000_000L),0x1d00ffffL,0,null),java.math.BigInteger.ONE,h); }
            public StoredBlock tip() { return at(20); }
        };
        String fileA=WalletStorageManager.saveWallet(context,a), fileB=WalletStorageManager.saveWallet(context,b);
        Controlled network=new Controlled(); BitcoinService service=new BitcoinService(context,network);
        try {
            for(int i=0;i<5;i++) {
                service.submit(()->service.openWallet(fileB)).get();
                BitcoinNetworkManager.Capture capture=network.access.capture(fileB);
                org.bitcoinj.core.Context.propagate(capture.context);
                Wallet synced=new ElectrumSync(server,server,headers,new JSONObject(),()->true,s->{}).synchronize(capture.wallet);
                assertTrue(network.access.apply(fileB,capture,synced));
                network.verified=true;
                service.submit(()->{service.requireReady(); return service.preparePayment(a.currentReceiveAddress().toString(),"0.001","1");}).get();
                assertEquals(Coin.CENT,service.getBalance());
                network.verified=false;
                assertThrows(java.util.concurrent.ExecutionException.class,()->service.submit(()->{service.requireReady();return true;}).get());
                service.submit(()->service.openWallet(fileA)).get();
                Wallet reopened=WalletStorageManager.loadWallet(context,fileB);
                assertEquals(Coin.CENT,WalletPolicy.getBalance(reopened));
                assertEquals(address,reopened.getTransaction(incoming.getTxId()).getOutput(0).getScriptPubKey().getToAddress(WalletPolicy.NETWORK).toString());
                assertEquals(TransactionConfidence.ConfidenceType.BUILDING,reopened.getTransaction(incoming.getTxId()).getConfidence().getConfidenceType());
                assertEquals(1,reopened.getWatchedScripts().size());
                assertFalse(reopened.calculateAllSpendCandidates(true,true).isEmpty());
            }
        } finally {
            service.submit(()->{service.shutdown();return true;}).get();
            WalletStorageManager.deleteWallet(context,fileA); WalletStorageManager.deleteWallet(context,fileB);
        }
    }
    @Test public void invalidProtobufCannotReplaceEncryptedWallet() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext(); new org.bitcoinj.core.Context();
        Wallet original=WalletPolicy.createWallet(); String file=WalletStorageManager.saveWallet(context,original);
        byte[] before=java.nio.file.Files.readAllBytes(new java.io.File(context.getFilesDir(),file).toPath());
        Wallet broken=new Wallet(WalletPolicy.NETWORK,KeyChainGroup.createBasic(WalletPolicy.NETWORK)) {
            @Override public void saveToFileStream(java.io.OutputStream output) throws java.io.IOException { output.write(new byte[]{0}); }
        };
        try {
            assertThrows(Exception.class,()->WalletStorageManager.updateWallet(context,file,broken));
            assertArrayEquals(before,java.nio.file.Files.readAllBytes(new java.io.File(context.getFilesDir(),file).toPath()));
            assertEquals(WalletPolicy.walletIdentity(original),WalletPolicy.walletIdentity(WalletStorageManager.loadWallet(context,file)));
        } finally { WalletStorageManager.deleteWallet(context,file); }
    }
    @Test public void changeBeforeScheduledSaveRejectsCapturedWallet() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext(); new org.bitcoinj.core.Context();
        String file=WalletStorageManager.saveWallet(context,WalletPolicy.createWallet());
        Controlled network=new Controlled(); BitcoinService service=new BitcoinService(context,network);
        try {
            service.submit(()->service.openWallet(file)).get();
            BitcoinNetworkManager.Capture capture=network.access.capture(file);
            service.submit(()->{
                Transaction tx=new Transaction(); tx.addInput(Sha256Hash.of(new byte[]{43}),0,ScriptBuilder.createEmpty());
                tx.addOutput(Coin.CENT,service.requireWallet().getWallet().currentReceiveAddress());
                service.requireWallet().getWallet().commitTx(tx);
                return true;
            }).get();
            assertFalse(network.access.apply(file,capture,capture.wallet));
            assertEquals(1,service.requireWallet().getWallet().getTransactions(true).size());
        } finally {
            service.submit(()->{service.shutdown();return true;}).get(); WalletStorageManager.deleteWallet(context,file);
        }
    }}

