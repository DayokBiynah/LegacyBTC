package com.example.legacybtc;

import android.content.Context;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;
import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Electrum is the only network transport. bitcoinj verifies headers and signs locally. */
public class BitcoinNetworkManager {
    static final class StateConflict extends Exception { }
    interface WalletAccess {
        Capture capture(String file) throws Exception;
        boolean apply(String file, Capture capture, Wallet wallet) throws Exception;
    }
    static final class Capture {
        final Wallet wallet; final long revision, selection;
        final org.bitcoinj.core.Context context;
        Capture(Wallet wallet, long revision, long selection) { this.wallet = wallet; this.revision = revision; this.selection = selection; this.context = org.bitcoinj.core.Context.get(); }
    }
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "electrum-network"));
    private final AtomicLong session = new AtomicLong();
    private volatile WalletAccess access;
    private volatile Wallet connectedWallet;
    private volatile boolean started, ready;
    private volatile long verifiedAt, median;
    private volatile String status = WalletPolicy.NETWORK_LABEL + ": offline";
    private volatile ElectrumClient primary, witness;
    private volatile int connections;
    private ElectrumHeaderStore headers;
    private BlockChain chain;
    private int failures;
    private final java.util.List<CompletableFuture<Void>> readyWaiters=new java.util.concurrent.CopyOnWriteArrayList<>();
    public CompletableFuture<Void> awaitReady() {
        CompletableFuture<Void> result=new CompletableFuture<>();readyWaiters.add(result);
        result.whenComplete((value,error)->readyWaiters.remove(result));
        if(isReady())result.complete(null);return result;
    }
    private volatile long estimatedFee;
    private volatile String verifiedTip="";
    private final ConcurrentMap<String,PlanEvidence> planEvidence=new ConcurrentHashMap<>();
    private final Set<String> verifyingPlans=ConcurrentHashMap.newKeySet();
    private static final class PlanEvidence {
        final InheritanceOutputVerifier.Evidence evidence; final long token,at; final String tip,message;
        PlanEvidence(InheritanceOutputVerifier.Evidence e,long token,String tip,String message) {this.evidence=e;this.token=token;this.tip=tip;this.message=message;at=android.os.SystemClock.elapsedRealtime();}
    }
    private String planKey(InheritancePlan plan) {return plan.getId()+":"+plan.fundingTransaction().getTxId()+":"+plan.getOutputIndex()+":"+plan.getUnlockTime();}
    InheritanceOutputVerifier.Evidence inheritanceEvidence(InheritancePlan plan) {
        if(!isReady() || !plan.isFunded())return null;
        String key=planKey(plan); PlanEvidence saved=planEvidence.get(key); long now=android.os.SystemClock.elapsedRealtime();
        boolean current=saved!=null && saved.token==session.get() && saved.tip.equals(verifiedTip);
        if(current && saved.evidence!=null && now-saved.at<45_000)return saved.evidence;
        if((!current || now-saved.at>5_000) && verifyingPlans.add(key)) {
            long token=session.get(); String tip=verifiedTip;
            io.execute(()->{
                try {
                    if(!active(token)||!isReady())return;
                    InheritanceOutputVerifier.Evidence e=InheritanceOutputVerifier.verify(plan,primary,witness,new ElectrumSync.Headers(){
                        public StoredBlock at(int h)throws Exception{return headerAt(h,token);}
                        public StoredBlock tip(){return headers.getChainHead();}
                    },median);
                    verifyWitnessTip();
                    if(active(token) && isReady() && tip.equals(verifiedTip))planEvidence.put(key,new PlanEvidence(e,token,tip,"Verified inheritance outpoint."));
                }catch(Exception error){
                    WalletDiagnostics.failure("independent inheritance outpoint verification",error);
                    if(active(token))planEvidence.put(key,new PlanEvidence(null,token,tip,inheritanceFailureMessage(error)));
                }finally{verifyingPlans.remove(key);}
            });
        }
        return null;
    }
    private static String inheritanceFailureMessage(Exception error) {
        if(error instanceof InheritanceOutputVerifier.VerificationFailure)return error.getMessage();
        if(isTransportFailure(error))return "Electrum transport unavailable during independent funding verification; retry when connected.";
        if(error instanceof org.bitcoinj.script.ScriptException)return "Reported inheritance spend failed local script verification; claiming is blocked.";
        return "Invalid inheritance response or changed blockchain anchor; no funding/spending eligibility can be published. See sanitized diagnostics.";
    }
    String inheritanceVerificationMessage(InheritancePlan plan) {
        PlanEvidence value=planEvidence.get(planKey(plan));
        return value==null ? "Verifying funding directly with Electrum; package bytes do not prove funding or maturity." : value.message;
    }

    void setWalletAccess(WalletAccess access) { this.access = access; }

    public void start(Context context, Wallet wallet) throws Exception {
        start(context, wallet, BuildConfig.MAINNET ? "legacybtc_wallet_mainnet_session.enc" : "legacybtc_wallet_session.enc");
    }
    public synchronized void start(Context context, Wallet wallet, String file) throws Exception {
        WalletPolicy.requireNetwork(wallet);
        long token = session.incrementAndGet(); connectedWallet = wallet; started = true; ready = false; verifiedAt = 0;
        wallet.setTransactionBroadcaster(null);
        status = WalletPolicy.NETWORK_LABEL + ": connecting to Electrum";
        io.execute(() -> synchronize(context.getApplicationContext(), file, token));
    }
    public synchronized void stop() {
        for(CompletableFuture<Void> waiter:readyWaiters)waiter.completeExceptionally(new IllegalStateException("Wallet connection stopped."));
        planEvidence.clear();
        session.incrementAndGet(); started = ready = false; connectedWallet = null; status = WalletPolicy.NETWORK_LABEL + ": offline";
        // Keep public network headers and TLS sessions across wallet switches. No wallet is subscribed.
    }
    private void synchronize(Context context, String file, long token) {
        if (!active(token)) return;
        org.bitcoinj.core.Context.propagate(new org.bitcoinj.core.Context());
        String operation = "blockchain verification";
        try {
            if (headers == null) { headers = new ElectrumHeaderStore(context); chain = new BlockChain(WalletPolicy.NETWORK, headers); }
            operation = "Electrum transport";
            connect(context);
            operation = "blockchain verification";
            updateHeaders(token);
            operation = "wallet snapshot/serialization";
            Capture capture = access == null ? capture(connectedWallet,0,0) : access.capture(file);
            org.bitcoinj.core.Context.propagate(capture.context);
            JSONObject cache = readCache(context, file);
            ElectrumSync sync = new ElectrumSync(primary, witness, new ElectrumSync.Headers() {
                public StoredBlock at(int height) throws Exception { return headerAt(height,token); }
                public StoredBlock tip() { return headers.getChainHead(); }
            }, cache, () -> active(token), message -> { if (active(token)) status = WalletPolicy.NETWORK_LABEL + ": " + message; });
            operation = "blockchain verification";
            Wallet updated = sync.synchronize(capture.wallet);
            verifyWitnessTip();
            if (!active(token)) return;
            operation = "wallet persistence";
            boolean accepted = access == null || access.apply(file, capture, updated);
            if (!active(token)) return;
            if (!accepted) { ready = false; status = WalletPolicy.NETWORK_LABEL + ": synchronization state conflict; wallet changed; retrying."; schedule(context,file,token,1); return; }
            WalletStorageManager.writeEncrypted(context, cacheFile(file), cache.toString().getBytes(StandardCharsets.UTF_8));
            operation = "blockchain verification";
            List<Long> times = new ArrayList<>(); StoredBlock head = headers.getChainHead();
            for (int h = head.getHeight(); h > head.getHeight()-11 && h >= 0; h--) times.add(headerAt(h,token).getHeader().getTimeSeconds());
            if (times.size() != 11) throw new IOException("Not enough verified headers for chain time");
            Collections.sort(times);
            synchronized (this) {
                if (!active(token)) return;
                connectedWallet = updated; verifiedTip=head.getHeader().getHashAsString(); median = times.get(5); verifiedAt = android.os.SystemClock.elapsedRealtime(); ready = true; failures = 0;
                status = WalletPolicy.NETWORK_LABEL + ": Electrum verified at height " + head.getHeight() + " (2 servers)";
                for(CompletableFuture<Void> waiter:readyWaiters)waiter.complete(null);
            }
            try {
                long a = ElectrumProof.feeRate(primary.request("blockchain.estimatefee", new JSONArray().put(6)));
                long b = ElectrumProof.feeRate(witness.request("blockchain.estimatefee", new JSONArray().put(6)));
                estimatedFee = Math.max(a,b);
            } catch (Exception unavailable) { estimatedFee = 0; }
            schedule(context,file,token,20);
        } catch (Exception error) {
            if (!active(token)) return;
            WalletDiagnostics.failure("synchronize: " + operation, error);
            ready = false; failures++;
            String category = failureCategory(operation,error);
            boolean remote = category.equals("Electrum transport");
            if (remote) closeConnections();
            // Reopen the transactional header store after any interrupted batch.
            if ((remote || operation.equals("blockchain verification")) && headers != null) { headers.close(); headers = null; chain = null; }
            status = WalletPolicy.NETWORK_LABEL + ": " + (remote ? "Electrum transport unavailable" : category + " failed") + ". Sending paused; cached data; retrying.";
            schedule(context,file,token,Math.min(60, (1L << Math.min(failures,5))) + ThreadLocalRandom.current().nextInt(3));
        }
    }
    private void schedule(Context context, String file, long token, long seconds) {
        if (active(token)) io.schedule(() -> synchronize(context,file,token),seconds,TimeUnit.SECONDS);
    }
    private boolean active(long token) { return started && session.get() == token; }
    private void connect(Context context) throws Exception {
        if (primary != null && witness != null) return;
        String[] endpoints = BuildConfig.REGTEST ? new String[]{"10.0.2.2:51002","10.0.2.2:51003"} : BuildConfig.MAINNET
                ? new String[]{"electrum.blockstream.info:50002", "electrum.jhoenicke.de:50002", "e.keff.org:50002", "electrum.diynodes.com:50022"}
                : new String[]{"electrum.blockstream.info:60002", "blackie.c3-soft.com:57006", "testnet.aranguren.org:51002"};
        List<String> pool = new ArrayList<>(Arrays.asList(endpoints)); Collections.rotate(pool, failures % pool.size());
        android.content.SharedPreferences settings = context.getSharedPreferences(WalletPolicy.NETWORK_ID + "_electrum", Context.MODE_PRIVATE);
        String host = settings.getString("host", "");
        if (host != null && !host.isEmpty()) pool.add(0, new NetworkSettings(host,Integer.toString(settings.getInt("port",50002))).getHost() + ":" + settings.getInt("port",50002));
        Set<String> hosts = new HashSet<>(); Exception last = null;
        for (String endpoint : pool) {
            String[] parts = endpoint.split(":"); if (!hosts.add(BuildConfig.REGTEST ? endpoint : parts[0])) continue;
            ElectrumClient candidate = null;
            try {
                candidate = new ElectrumClient(parts[0],Integer.parseInt(parts[1]));
                candidate.request("server.version", new JSONArray().put("LegacyBTC/1.0").put("1.4"));
                JSONObject features = (JSONObject)candidate.request("server.features", new JSONArray());
                if (!features.getString("genesis_hash").equals(ElectrumHeaderStore.parameters().getGenesisBlock().getHashAsString())) throw new IOException("Wrong Electrum network");
                if (primary == null) primary = candidate; else { witness = candidate; break; }
                connections++;
            } catch (Exception error) { last = error; if (candidate != null) try { candidate.close(); } catch (IOException ignored) { } }
        }
        connections = (primary == null ? 0 : 1) + (witness == null ? 0 : 1);
        if (connections != 2) throw new IOException("Two independent TLS Electrum servers are required", last);
    }
    private void updateHeaders(long token) throws Exception {
        JSONObject advertised = (JSONObject)primary.request("blockchain.headers.subscribe", new JSONArray());
        int target = advertised.getInt("height");
        if (target < 0 || target > 20_000_000) throw new IOException("Invalid tip height");
        int common = Math.min(target, headers.getChainHead().getHeight());
        if (common >= 11) headerAt(common-10,token);
        while (common > 0 && !headerAt(common,token).getHeader().getHash().equals(readHeader(primary.request("blockchain.block.header",new JSONArray().put(common))).getHash())) {
            if (!active(token)) return;
            common--;
        }
        int next = common+1;
        while (next <= target) {
            if (!active(token)) return;
            status = WalletPolicy.NETWORK_LABEL + ": verifying shared headers " + next + "/" + target;
            int requested = Math.min(2016,target-next+1);
            JSONObject batch = (JSONObject)primary.request("blockchain.block.headers",new JSONArray().put(next).put(requested));
            int count = batch.getInt("count"); String raw = batch.getString("hex");
            if (count < 1 || count > requested || raw.length() != count*160) throw new IOException("Invalid header batch");
            headers.begin();
            try {
                for (int i = 0; i < count; i++) {
                    Block block = readHeader(raw.substring(i*160,(i+1)*160));
                    StoredBlock parent = headers.get(block.getPrevBlockHash());
                    if (parent != null) headers.enforceCheckpoint(block,parent.getHeight()+1);
                    List<Long> times = new ArrayList<>();
                    for (int n = 0; n < 11 && parent != null; n++) {
                        times.add(parent.getHeader().getTimeSeconds()); parent = headers.get(parent.getHeader().getPrevBlockHash());
                    }
                    if (times.isEmpty()) throw new IOException("Missing header parent");
                    Collections.sort(times);
                    if (block.getTimeSeconds() <= times.get(times.size()/2)) throw new IOException("Header violates median time past");
                    if (!chain.add(block)) throw new IOException("Unconnected header");
                }
                headers.commit();
            } finally { headers.end(); }
            next += count;
        }
        StoredBlock head = headers.getChainHead();
        if (head.getHeight() != target || !head.getHeader().getHash().equals(readHeader(advertised.getString("hex")).getHash()))
            throw new IOException("Server tip does not extend the strongest verified chain");
        if (head.getHeader().getTimeSeconds() < java.time.Instant.now().minusSeconds(24*3600).getEpochSecond())
            throw new IOException("Electrum chain tip is stale");
        verifyWitnessTip();
    }
    private void verifyWitnessTip() throws Exception {
        StoredBlock head = headers.getChainHead();
        Block other = readHeader(witness.request("blockchain.block.header", new JSONArray().put(head.getHeight())));
        if (!other.getHash().equals(head.getHeader().getHash())) throw new IOException("Electrum servers disagree about the chain");
        // Detect a tip change during history/UTXO collection; never publish a mixed snapshot.
        Block current = readHeader(primary.request("blockchain.block.header", new JSONArray().put(head.getHeight())));
        if (!current.getHash().equals(head.getHeader().getHash())) throw new IOException("Chain reorganized during verification");
    }
    private StoredBlock headerAt(int height, long token) throws Exception {
        try { return headers.at(height); }
        catch (org.bitcoinj.store.BlockStoreException missing) {
            StoredBlock child = headers.nearestAbove(height);
            while (child.getHeight() > height) {
                if (!active(token)) throw new IOException("Wallet session changed");
                int high = child.getHeight()-1, low = Math.max(height,high-2015), count = high-low+1;
                status = WalletPolicy.NETWORK_LABEL + ": verifying historical headers " + low + "–" + high;
                JSONObject result = (JSONObject)primary.request("blockchain.block.headers",new JSONArray().put(low).put(count));
                String raw = result.getString("hex");
                if (result.getInt("count") != count || raw.length() != count*160) throw new IOException("Incomplete historical header batch");
                headers.begin();
                try {
                    for (int i = count-1; i >= 0; i--) {
                        child = ElectrumProof.ancestor(child,readHeader(raw.substring(i*160,(i+1)*160)));
                        if (child.getHeight() == 0 && !child.getHeader().getHash().equals(ElectrumHeaderStore.parameters().getGenesisBlock().getHash()))
                            throw new IOException("Historical headers have the wrong genesis");
                        headers.putAncestor(child);
                    }
                    headers.commit();
                } finally { headers.end(); }
            }
            return child;
        }
    }
    static Block readHeader(Object hex) throws IOException {
        if (!(hex instanceof String) || !((String)hex).matches("[a-fA-F0-9]{160}")) throw new IOException("Invalid header bytes");
        return Block.read(ByteBuffer.wrap(Hex.decode((String)hex)));
    }
    private static JSONObject readCache(Context context, String file) {
        try { return new JSONObject(new String(WalletStorageManager.readEncrypted(context,cacheFile(file)),StandardCharsets.UTF_8)); }
        catch (Exception missingOrDamaged) { return new JSONObject(); } // Re-fetch proofs; never infer confirmation from a missing cache.
    }
    private static String cacheFile(String file) { WalletStorageManager.validateFileName(file); return "electrum_" + file; }
    static Wallet copy(Wallet wallet) throws Exception {
        return capture(wallet, 0, 0).wallet;
    }
    static Capture capture(Wallet wallet, long revision, long selection) throws Exception {
        // bitcoinj locks the wallet during saveToFileStream. The service also serializes all live mutations.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try { wallet.saveToFileStream(bytes); }
        catch (Exception error) { WalletDiagnostics.failure("in-memory wallet snapshot: protobuf write", error); throw error; }
        byte[] plain = bytes.toByteArray();
        org.bitcoinj.core.Context original = org.bitcoinj.core.Context.get();
        try {
            // Confidence tables are per Context and keyed by txid. A snapshot must not share live confidence.
            org.bitcoinj.core.Context.propagate(new org.bitcoinj.core.Context());
            return new Capture(Wallet.loadFromFileStream(new ByteArrayInputStream(plain)), revision, selection);
        } catch (Exception error) {
            WalletDiagnostics.failure("in-memory wallet snapshot: protobuf reload", error); throw error;
        } finally {
            org.bitcoinj.core.Context.propagate(original);
            Arrays.fill(plain,(byte)0);
        }
    }
    public void rescan(Context context, Wallet wallet, String file) throws Exception { start(context,wallet,file); }
    public boolean isReady() { return ready && started && connections == 2 && android.os.SystemClock.elapsedRealtime()-verifiedAt < 90_000; }
    public int getConnectedPeerCount() { return started ? connections : 0; }
    public Wallet getConnectedWallet() { return connectedWallet; }
    public boolean isNetworkStarted() { return started; }
    public String getStatus() { return ready && !isReady() ? WalletPolicy.NETWORK_LABEL + ": cached data; verification expired" : status; }
    public long medianTimePast() { if (!isReady()) throw new IllegalStateException("Verify Electrum chain state before checking a timelock."); return median; }
    public long getEstimatedFeeRate() { return isReady() ? estimatedFee : 0; }
    public CompletableFuture<Transaction> broadcast(Transaction tx) {
        CompletableFuture<Transaction> result = new CompletableFuture<>();
        long token = session.get(); Wallet owner = connectedWallet;
        io.execute(() -> {
            try {
                if (!active(token) || !isReady() || owner != connectedWallet) throw new IOException("Wallet connection changed; retry the saved transaction");
                Object id = primary.request("blockchain.transaction.broadcast",new JSONArray().put(Hex.encode(tx.serialize())));
                if (!(id instanceof String) || !tx.getTxId().toString().equals(ElectrumProof.txid((String)id))) throw new IOException("Broadcast result does not match the saved transaction");
                result.complete(tx);
            } catch (Exception error) { ready = false; result.completeExceptionally(error); }
        });
        return result;
    }
    void disconnectServers() { closeConnections(); }
    private void closeConnections() {
        ElectrumClient a = primary, b = witness; primary = witness = null; connections = 0;
        try { if (a != null) a.close(); } catch (IOException ignored) { }
        try { if (b != null) b.close(); } catch (IOException ignored) { }
    }
    void shutdown() {
        stop(); closeConnections(); io.execute(() -> { if (headers != null) headers.close(); }); io.shutdown();
    }
    static String failureCategory(String operation, Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof StateConflict) return "synchronization state conflict";
        return isTransportFailure(error) || operation.equals("Electrum transport") ? "Electrum transport" : operation;
    }
    static boolean isTransportFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ElectrumClient.TransportException || cause instanceof java.net.SocketException
                    || cause instanceof java.net.SocketTimeoutException || cause instanceof javax.net.ssl.SSLException
                    || cause instanceof java.net.UnknownHostException || cause instanceof java.io.EOFException) return true;
        }
        return false;
    }
}


