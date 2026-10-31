package com.example.legacybtc;

import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.*;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ElectrumSyncTest {
    static class Server implements ElectrumRpc {
        final Map<String,Transaction> txs = new LinkedHashMap<>();
        final Map<String,Integer> heights = new HashMap<>();
        final Map<String,JSONArray> history = new HashMap<>();
        final Map<String,JSONArray> utxos = new HashMap<>();
        final Map<String,JSONObject> balances = new HashMap<>();
        int rawReads, proofReads; boolean lie;
        void income(Transaction tx, int height) throws Exception {
            txs.put(tx.getTxId().toString(),tx); heights.put(tx.getTxId().toString(),height);
            history.clear(); utxos.clear(); balances.clear();
            Set<String> spent=new HashSet<>();
            for(Transaction item:txs.values()) for(TransactionInput input:item.getInputs()) spent.add(input.getOutpoint().toString());
            for(Transaction item:txs.values()) {
                String id=item.getTxId().toString(); int h=heights.get(id); Map<String,Long> deltas=new HashMap<>();
                for(TransactionOutput output:item.getOutputs()) {
                    String hash=ElectrumClient.scriptHash(output.getScriptPubKey().getProgram());
                    deltas.put(hash,deltas.getOrDefault(hash,0L)+output.getValue().value);
                    if(!spent.contains(output.getOutPointFor().toString())) utxos.computeIfAbsent(hash,k->new JSONArray()).put(new JSONObject().put("tx_hash",id).put("tx_pos",output.getIndex()).put("value",output.getValue().value).put("height",Math.max(0,h)));
                }
                for(TransactionInput input:item.getInputs()) {
                    Transaction parent=txs.get(input.getOutpoint().getHash().toString());
                    if(parent==null) continue;
                    TransactionOutput output=parent.getOutput(input.getOutpoint().getIndex());
                    String hash=ElectrumClient.scriptHash(output.getScriptPubKey().getProgram());
                    deltas.put(hash,deltas.getOrDefault(hash,0L)-output.getValue().value);
                }
                for(Map.Entry<String,Long> entry:deltas.entrySet()) {
                    String hash=entry.getKey(); history.computeIfAbsent(hash,k->new JSONArray()).put(new JSONObject().put("tx_hash",id).put("height",h));
                    JSONObject balance=balances.computeIfAbsent(hash,k->new JSONObject()); String category=h>0?"confirmed":"unconfirmed";
                    balance.put(category,balance.optLong(category)+entry.getValue());
                }
            }
        }
        @Override public Object request(String method, JSONArray params) throws Exception {
            String value = params.getString(0);
            switch (method) {
                case "blockchain.scripthash.get_history": return history.getOrDefault(value,new JSONArray());
                case "blockchain.scripthash.listunspent": return utxos.getOrDefault(value,new JSONArray());
                case "blockchain.scripthash.get_balance":
                    JSONObject b = balances.getOrDefault(value,new JSONObject());
                    return new JSONObject().put("confirmed",b.optLong("confirmed")+(lie?1:0)).put("unconfirmed",b.optLong("unconfirmed"));
                case "blockchain.transaction.get": rawReads++; return Hex.encode(txs.get(value).serialize());
                case "blockchain.transaction.get_merkle": proofReads++; return new JSONObject().put("block_height",heights.get(value)).put("pos",0).put("merkle",new JSONArray());
                default: throw new AssertionError(method);
            }
        }
        @Override public List<Object> batch(String method,List<JSONArray> params) throws Exception {
            List<Object> result = new ArrayList<>(); for (JSONArray p : params) result.add(request(method,p)); return result;
        }
        @Override public void close() { }
    }
    private Transaction income(Wallet wallet,int branch,int index) {
        DeterministicKeyChain chain = wallet.getActiveKeyChain(); chain.maybeLookAhead();
        ECKey key = chain.getKeyByPath(chain.getAccountPath().extend(new ChildNumber(branch,false),new ChildNumber(index,false)),false);
        Transaction tx = new Transaction();
        tx.addInput(Sha256Hash.of(new byte[]{(byte)branch,(byte)index,19}),0,ScriptBuilder.createEmpty());
        tx.addOutput(Coin.CENT,key.toAddress(chain.getOutputScriptType(),WalletPolicy.NETWORK)); return tx;
    }
    private ElectrumSync.Headers headers(Transaction tx) {
        return new ElectrumSync.Headers() {
            public StoredBlock at(int height) {
                Block block = new Block(1,Sha256Hash.ZERO_HASH,tx.getTxId(),java.time.Instant.ofEpochSecond(1_800_000_000L),0x1d00ffffL,0,null);
                return new StoredBlock(block,java.math.BigInteger.ONE,height);
            }
            public StoredBlock tip() { return at(20); }
        };
    }
    @Test public void receivingAndChangeHistoryUseExistingPathsAndCacheProofs() throws Exception {
        new org.bitcoinj.core.Context(); Wallet original = WalletPolicy.createWallet();
        Transaction receive = income(original,0,90); Server server = new Server(); server.income(receive,1);
        JSONObject cache = new JSONObject();
        Wallet synced = new ElectrumSync(server,server,headers(receive),cache,()->true,s->{}).synchronize(BitcoinNetworkManager.copy(original));
        assertEquals(Coin.CENT,WalletPolicy.getBalance(synced));
        assertTrue(synced.getActiveKeyChain().getIssuedExternalKeys() >= 91);
        assertEquals(1,server.proofReads);
        new ElectrumSync(server,server,headers(receive),cache,()->true,s->{}).synchronize(BitcoinNetworkManager.copy(synced));
        assertEquals(1,server.rawReads); assertEquals(1,server.proofReads);
        Wallet changeWallet = WalletPolicy.createWallet(); Transaction change = income(changeWallet,1,95);
        Server changeServer = new Server(); changeServer.income(change,1);
        Wallet changeSynced = new ElectrumSync(changeServer,changeServer,headers(change),new JSONObject(),()->true,s->{}).synchronize(changeWallet);
        assertTrue(changeSynced.getActiveKeyChain().getIssuedInternalKeys() >= 96);
        assertEquals(Coin.CENT,WalletPolicy.getBalance(changeSynced));
        assertTrue(changeSynced.findKeyFromPubKey(changeWallet.getActiveKeyChain().getKeyByPath(changeWallet.getActiveKeyChain().getAccountPath().extend(new ChildNumber(1,false),new ChildNumber(95,false)),false).getPubKey()).hasPrivKey());
    }
    @Test public void disagreementOrInvalidUtxosCannotPublishBalances() throws Exception {
        new org.bitcoinj.core.Context(); Wallet wallet = WalletPolicy.createWallet(); Transaction tx = income(wallet,0,0);
        Server primary = new Server(), other = new Server(); primary.income(tx,1);
        assertThrows(java.io.IOException.class,()->new ElectrumSync(primary,other,headers(tx),new JSONObject(),()->true,s->{}).synchronize(BitcoinNetworkManager.copy(wallet)));
        primary.lie = true;
        assertThrows(java.io.IOException.class,()->new ElectrumSync(primary,primary,headers(tx),new JSONObject(),()->true,s->{}).synchronize(BitcoinNetworkManager.copy(wallet)));
        assertEquals(Coin.ZERO,WalletPolicy.getBalance(wallet));
    }
    @Test public void unsignedNetworkDataCannotReplaceRawTransactionOrProof() throws Exception {
        new org.bitcoinj.core.Context(); Wallet wallet = WalletPolicy.createWallet(); Transaction tx = income(wallet,0,0);
        assertThrows(java.io.IOException.class,()->ElectrumSync.readTransaction(Sha256Hash.ZERO_HASH.toString(),Hex.encode(tx.serialize())));
        JSONObject proof = new JSONObject().put("block_height",1).put("pos",0).put("merkle",new JSONArray());
        ElectrumProof.verifyMerkle(tx.getTxId().toString(),proof,1,tx.getTxId());
        assertThrows(java.io.IOException.class,()->ElectrumProof.verifyMerkle(tx.getTxId().toString(),proof,2,tx.getTxId()));
        assertThrows(java.io.IOException.class,()->ElectrumProof.verifyMerkle(tx.getTxId().toString(),proof,1,Sha256Hash.ZERO_HASH));
        proof.put("pos",1);
        assertThrows(java.io.IOException.class,()->ElectrumProof.verifyMerkle(tx.getTxId().toString(),proof,1,tx.getTxId()));
    }
    @Test public void feeConversionIsExactAndRejectsUnavailableOrExcessiveEstimates() {
        assertEquals(5,ElectrumProof.feeRate("0.00005"));
        assertEquals(2,ElectrumProof.feeRate("0.00001001"));
        for (String value : new String[]{"-1","0","NaN","1e30","0.01001"}) assertThrows(RuntimeException.class,()->ElectrumProof.feeRate(value));
    }
    @Test public void cancellationLeavesOriginalWalletAndPendingTransactionUntouched() throws Exception {
        new org.bitcoinj.core.Context(); Wallet wallet = WalletPolicy.createWallet(); Transaction tx = income(wallet,0,0);
        tx.getConfidence().setSource(TransactionConfidence.Source.SELF); wallet.commitTx(tx);
        tx.setMemo("saved fixture"); tx.setPurpose(Transaction.Purpose.RAISE_FEE);
        wallet.addWatchedAddress(new ECKey().toAddress(ScriptType.P2WPKH,WalletPolicy.NETWORK));
        byte[] bytes = tx.serialize(); Server server = new Server();
        assertThrows(java.io.IOException.class,()->new ElectrumSync(server,server,headers(tx),new JSONObject(),()->false,s->{}).synchronize(BitcoinNetworkManager.copy(wallet)));
        assertArrayEquals(bytes,wallet.getTransaction(tx.getTxId()).serialize());
        Wallet synced = new ElectrumSync(server,server,headers(tx),new JSONObject(),()->true,s->{}).synchronize(BitcoinNetworkManager.copy(wallet));
        assertArrayEquals(bytes,synced.getTransaction(tx.getTxId()).serialize());
        synced = BitcoinNetworkManager.copy(synced);
        assertEquals("saved fixture",synced.getTransaction(tx.getTxId()).getMemo());
        assertEquals(Transaction.Purpose.RAISE_FEE,synced.getTransaction(tx.getTxId()).getPurpose());
        assertEquals(1,synced.getWatchedScripts().size());
        assertEquals(TransactionConfidence.Source.SELF,synced.getTransaction(tx.getTxId()).getConfidence().getSource());
    }
    @Test public void fundedInheritanceRemainsExcludedAndBeneficiaryClaimUsesElectrumState() throws Exception {
        new org.bitcoinj.core.Context();
        Wallet owner=WalletPolicy.createWallet(), beneficiary=WalletPolicy.createWallet();
        ECKey ownerKey=owner.currentReceiveKey(), beneficiaryKey=beneficiary.currentReceiveKey();
        InheritancePlan plan=new InheritancePlan(new Owner("Owner",ownerKey.getPublicKeyAsHex(),owner.currentReceiveAddress().toString()),
                new Beneficiary("Beneficiary",beneficiaryKey.getPublicKeyAsHex(),beneficiary.currentReceiveAddress().toString()),Coin.valueOf(500_000),3600,1_800_000_000L);
        Transaction income=income(owner,0,0); Server server=new Server(); server.income(income,1);
        ElectrumSync.Headers chain=new ElectrumSync.Headers() {
            public StoredBlock at(int height) {
                String id=server.heights.entrySet().stream().filter(e->e.getValue()==height).findFirst().map(Map.Entry::getKey).orElse(income.getTxId().toString());
                return new StoredBlock(new Block(1,Sha256Hash.ZERO_HASH,Sha256Hash.wrap(id),java.time.Instant.ofEpochSecond(plan.getUnlockTime()+1),0x1d00ffffL,0,null),java.math.BigInteger.ONE,height);
            }
            public StoredBlock tip(){return at(20);}
        };
        owner=new ElectrumSync(server,server,chain,new JSONObject(),()->true,s->{}).synchronize(owner);
        owner.addWatchedAddress(InheritanceScript.address(plan));
        SendRequest fundingRequest=BitcoinPayments.prepare(owner,InheritanceScript.address(plan).toString(),"0.005","1");
        fundingRequest.signInputs=true; owner.signTransaction(fundingRequest); Transaction funding=fundingRequest.tx;
        for(int i=0;i<funding.getOutputs().size();i++) if(funding.getOutput(i).getValue().equals(plan.getAmount())) plan.attachFunding(funding,i);
        owner.addWatchedAddress(InheritanceScript.address(plan)); server.income(funding,2);
        owner=new ElectrumSync(server,server,chain,new JSONObject(),()->true,s->{}).synchronize(owner);
        assertTrue(WalletPolicy.getBalance(owner).isLessThan(Coin.valueOf(500_000)));
        beneficiary.addWatchedAddress(InheritanceScript.address(plan));
        beneficiary=new ElectrumSync(server,server,chain,new JSONObject(),()->true,s->{}).synchronize(beneficiary);
        assertEquals(Coin.ZERO,WalletPolicy.getBalance(beneficiary));
        Transaction confirmedFunding=beneficiary.getTransaction(funding.getTxId());
        assertEquals(TransactionConfidence.ConfidenceType.BUILDING,confirmedFunding.getConfidence().getConfidenceType());
        Transaction claim=InheritanceScript.spend(plan,confirmedFunding.getOutput(plan.getOutputIndex()),beneficiaryKey,beneficiary.currentReceiveAddress(),1,false);
        assertTrue(confirmedFunding.getOutput(plan.getOutputIndex()).isAvailableForSpending());
        server.income(claim,3);
        beneficiary=new ElectrumSync(server,server,chain,new JSONObject(),()->true,s->{}).synchronize(beneficiary);
        assertEquals(plan.getAmount().subtract(Coin.valueOf(400)),WalletPolicy.getBalance(beneficiary));
        assertFalse(beneficiary.getTransaction(funding.getTxId()).getOutput(plan.getOutputIndex()).isAvailableForSpending());
    }
    @Test public void signedWalletTransferSurvivesRepeatedVerifiedSyncAndProtobufReload() throws Exception {
        new org.bitcoinj.core.Context();
        Wallet a = WalletPolicy.createWallet(), b = WalletPolicy.createWallet();
        String identity = WalletPolicy.walletIdentity(b), address = b.currentReceiveAddress().toString();
        Transaction deposit = income(a,0,0);
        Server source = new Server(); source.income(deposit,1);
        a = new ElectrumSync(source,source,headers(deposit),new JSONObject(),()->true,s->{}).synchronize(a);
        SendRequest payment = BitcoinPayments.prepare(a,address,"0.005","1");
        payment.signInputs = true; a.signTransaction(payment);
        for (TransactionInput input : payment.tx.getInputs()) input.verify();
        a.commitTx(payment.tx);
        Server destination = new Server(); destination.income(payment.tx,2);
        JSONObject cache = new JSONObject();
        for (int i=0;i<5;i++) {
            b = new ElectrumSync(destination,destination,headers(payment.tx),cache,()->true,s->{}).synchronize(BitcoinNetworkManager.copy(b));
            b = BitcoinNetworkManager.copy(b);
            assertEquals(Coin.valueOf(500_000),WalletPolicy.getBalance(b));
            assertEquals(identity,WalletPolicy.walletIdentity(b));
            assertEquals(TransactionConfidence.ConfidenceType.BUILDING,b.getTransaction(payment.tx.getTxId()).getConfidence().getConfidenceType());
            assertFalse(b.calculateAllSpendCandidates(true,true).isEmpty());
            SendRequest spend = BitcoinPayments.prepare(b,a.currentReceiveAddress().toString(),"0.001","1");
            spend.signInputs = true; b.signTransaction(spend);
            for (TransactionInput input : spend.tx.getInputs()) input.verify();
        }
    }    @Test public void snapshotConfidenceIsIndependentOfLiveWallet() throws Exception {
        new org.bitcoinj.core.Context();
        Wallet live = WalletPolicy.createWallet(); Transaction receive = income(live,0,0);
        live.commitTx(receive);
        BitcoinNetworkManager.Capture snapshot = BitcoinNetworkManager.capture(live,7,9);
        assertNotSame(receive.getConfidence(),snapshot.wallet.getTransaction(receive.getTxId()).getConfidence());
        org.bitcoinj.core.Context original = org.bitcoinj.core.Context.get();
        try {
            org.bitcoinj.core.Context.propagate(snapshot.context);
            Server server = new Server(); server.income(receive,1);
            new ElectrumSync(server,server,headers(receive),new JSONObject(),()->true,s->{}).synchronize(snapshot.wallet);
            assertEquals(TransactionConfidence.ConfidenceType.BUILDING,snapshot.wallet.getTransaction(receive.getTxId()).getConfidence().getConfidenceType());
            assertEquals(TransactionConfidence.ConfidenceType.PENDING,receive.getConfidence().getConfidenceType());
        } finally { org.bitcoinj.core.Context.propagate(original); }
        assertEquals(7,snapshot.revision); assertEquals(9,snapshot.selection);
    }
    @Test public void localProtobufFailureIsNotTransportFailure() {
        Throwable local = new java.util.concurrent.ExecutionException(new UnreadableWalletException("fixture",new IllegalStateException("fixture")));
        assertFalse(BitcoinNetworkManager.isTransportFailure(local));
        assertEquals("wallet snapshot/serialization",BitcoinNetworkManager.failureCategory("wallet snapshot/serialization",local));
        assertEquals("wallet persistence",BitcoinNetworkManager.failureCategory("wallet persistence",local));
        assertEquals("synchronization state conflict",BitcoinNetworkManager.failureCategory("wallet snapshot/serialization",new java.util.concurrent.ExecutionException(new BitcoinNetworkManager.StateConflict())));
        assertTrue(BitcoinNetworkManager.isTransportFailure(new java.util.concurrent.ExecutionException(new java.net.SocketTimeoutException())));
        assertFalse(BitcoinNetworkManager.isTransportFailure(new java.io.IOException("bad Merkle proof")));
    }}




