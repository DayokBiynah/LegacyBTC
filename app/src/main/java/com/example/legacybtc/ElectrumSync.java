package com.example.legacybtc;

import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.*;
import org.bitcoinj.script.*;
import org.bitcoinj.wallet.*;
import org.json.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Builds a new wallet view off-thread. No live wallet changes occur before all checks pass. */
final class ElectrumSync {
    interface Headers {
        StoredBlock at(int height) throws Exception;
        StoredBlock tip() throws Exception;
    }
    static final int GAP = 20, BATCH = 20, MAX_ADDRESSES = 100_000, MAX_HISTORY = 100_000;
    private final ElectrumRpc primary, witness;
    private final Headers headers;
    private final BooleanSupplier active;
    private final Consumer<String> progress;
    private final Map<String, JSONArray> histories = new LinkedHashMap<>();
    private final Map<String, Integer> heights = new LinkedHashMap<>();
    private final Map<String, Transaction> transactions = new LinkedHashMap<>();
    private final JSONObject proofs;
    private final Map<String, Transaction> previous = new HashMap<>();

    ElectrumSync(ElectrumRpc primary, ElectrumRpc witness, Headers headers, JSONObject proofs,
                 BooleanSupplier active, Consumer<String> progress) {
        this.primary = primary; this.witness = witness; this.headers = headers;
        this.proofs = proofs; this.active = active; this.progress = progress;
    }
    void checkActive() throws IOException { if (!active.getAsBoolean()) throw new IOException("Wallet session changed"); }

    Wallet synchronize(Wallet wallet) throws Exception {
        for (Transaction tx : wallet.getTransactions(true)) previous.put(tx.getTxId().toString(), tx);
        for (DeterministicKeyChain chain : wallet.getActiveKeyChains()) {
            for (int branch = 0; branch < 2; branch++) {
                int issued = branch == 0 ? chain.getIssuedExternalKeys() : chain.getIssuedInternalKeys();
                if (issued < 0 || issued > MAX_ADDRESSES-GAP) throw new IOException("Issued address count exceeds the supported discovery limit");
                int end = Math.max(issued + GAP, Math.max(GAP, chain.getLookaheadSize()));
                int index = 0;
                chain.maybeLookAhead();
                while (index < end) {
                    checkActive();
                    if (end > MAX_ADDRESSES) throw new IOException("Address discovery limit reached; wallet retained. Contact support for a deeper scan.");
                    List<String> hashes = new ArrayList<>(); List<DeterministicKey> keys = new ArrayList<>();
                    for (int i = index; i < Math.min(end, index+BATCH); i++) {
                        DeterministicKey key = chain.getKeyByPath(chain.getAccountPath().extend(new ChildNumber(branch, false), new ChildNumber(i, false)), false);
                        keys.add(key); hashes.add(ElectrumClient.scriptHash(key.toAddress(chain.getOutputScriptType(), WalletPolicy.NETWORK)));
                    }
                    lookup(hashes);
                    for (int i = 0; i < hashes.size(); i++) {
                        if (histories.get(hashes.get(i)).length() > 0) {
                            chain.markKeyAsUsed(keys.get(i)); end = Math.max(end, index+i+1+GAP);
                        }
                    }
                    index += hashes.size();
                    progress.accept("Discovering " + (branch == 0 ? "receiving" : "change") + " addresses: " + index);
                }
            }
        }
        List<String> extra = new ArrayList<>();
        for (Script script : wallet.getWatchedScripts()) extra.add(ElectrumClient.scriptHash(script.getProgram()));
        for (ECKey key : wallet.getImportedKeys()) {
            extra.add(ElectrumClient.scriptHash(key.toAddress(ScriptType.P2PKH, WalletPolicy.NETWORK)));
            if (key.isCompressed()) extra.add(ElectrumClient.scriptHash(key.toAddress(ScriptType.P2WPKH, WalletPolicy.NETWORK)));
        }
        for (int i = 0; i < extra.size(); i += BATCH) lookup(extra.subList(i, Math.min(extra.size(), i+BATCH)));
        List<String> ids = new ArrayList<>(heights.keySet());
        for (String id : ids) {
            checkActive();
            Transaction old = previous.get(id);
            Transaction tx = old == null ? readTransaction(id, primary.request("blockchain.transaction.get", new JSONArray().put(id).put(false)))
                    : Transaction.read(ByteBuffer.wrap(old.serialize()));
            if (old != null) { tx.setMemo(old.getMemo()); tx.setPurpose(old.getPurpose()); tx.setExchangeRate(old.getExchangeRate()); tx.setUpdateTime(old.getUpdateTime()); tx.getConfidence().setSource(old.getConfidence().getSource()); }
            transactions.put(id, tx);
            int height = heights.get(id);
            if (height > 0) {
                StoredBlock block = headers.at(height);
                String anchor = height + ":" + block.getHeader().getHash();
                if (!anchor.equals(proofs.optString(id))) {
                    Object proof = primary.request("blockchain.transaction.get_merkle", new JSONArray().put(id).put(height));
                    if (!(proof instanceof JSONObject)) throw new IOException("Invalid transaction proof");
                    ElectrumProof.verifyMerkle(id, (JSONObject) proof, height, block.getHeader().getMerkleRoot());
                    proofs.put(id, anchor);
                }
            }
        }
        // A server may not erase known history on an unchanged chain.
        for (Transaction tx : previous.values()) {
            if (tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING && !heights.containsKey(tx.getTxId().toString())) {
                int height = tx.getConfidence().getAppearedAtChainHeight();
                String anchor = proofs.optString(tx.getTxId().toString());
                if (height >= 0 && height <= headers.tip().getHeight()) {
                    Sha256Hash block = headers.at(height).getHeader().getHash();
                    if (anchor.equals(height + ":" + block)
                            || (tx.getAppearsInHashes() != null && tx.getAppearsInHashes().containsKey(block)))
                        throw new IOException("Electrum omitted previously verified history");
                }
            }
        }
        validateScriptState();
        checkActive();
        rebuild(wallet);
        return wallet;
    }
    private void lookup(List<String> requested) throws Exception {
        List<String> hashes = new ArrayList<>();
        for (String hash : requested) if (!histories.containsKey(hash) && !hashes.contains(hash)) hashes.add(hash);
        if (hashes.isEmpty()) return;
        checkActive(); List<JSONArray> params = new ArrayList<>();
        for (String hash : hashes) params.add(new JSONArray().put(hash));
        List<Object> results = primary.batch("blockchain.scripthash.get_history", params);
        List<Object> comparison = witness.batch("blockchain.scripthash.get_history", params);
        for (int i = 0; i < hashes.size(); i++) {
            if (!(results.get(i) instanceof JSONArray) || !(comparison.get(i) instanceof JSONArray)) throw new IOException("Invalid history response");
            JSONArray history = (JSONArray)results.get(i);
            Map<String,Integer> normalized = normalize(history);
            if (!normalized.equals(normalize((JSONArray)comparison.get(i)))) throw new IOException("Electrum servers disagree about wallet history; retrying");
            histories.put(hashes.get(i), history);
            for (Map.Entry<String,Integer> entry : normalized.entrySet()) {
                Integer existing = heights.putIfAbsent(entry.getKey(), entry.getValue());
                if (existing != null && !existing.equals(entry.getValue())) throw new IOException("Inconsistent transaction heights");
            }
            if (heights.size() > MAX_HISTORY) throw new IOException("Wallet history limit exceeded; existing wallet retained");
        }
    }
    private Map<String,Integer> normalize(JSONArray history) throws Exception {
        if (history.length() > MAX_HISTORY) throw new IOException("Excessive history");
        Map<String,Integer> values = new TreeMap<>(); int tip = headers.tip().getHeight();
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.getJSONObject(i);
            String id = ElectrumProof.txid(item.getString("tx_hash")); int height = item.getInt("height");
            if (height < -1 || height > tip || values.put(id, height) != null) throw new IOException("Invalid history height or duplicate");
        }
        return values;
    }
    static Transaction readTransaction(String id, Object raw) throws Exception {
        if (!(raw instanceof String) || ((String)raw).length() > 2_000_000) throw new IOException("Invalid transaction size");
        ByteBuffer bytes = ByteBuffer.wrap(Hex.decode((String)raw)); Transaction tx = Transaction.read(bytes);
        if (bytes.hasRemaining() || !tx.getTxId().toString().equals(ElectrumProof.txid(id))) throw new IOException("Transaction ID mismatch");
        Transaction.verify(WalletPolicy.NETWORK, tx); return tx;
    }
    private String outputHash(TransactionOutput output) { return ElectrumClient.scriptHash(output.getScriptPubKey().getProgram()); }
    private TransactionOutput parent(TransactionInput input) {
        Transaction tx = transactions.get(input.getOutpoint().getHash().toString());
        long index = input.getOutpoint().getIndex();
        return tx != null && index >= 0 && index < tx.getOutputs().size() ? tx.getOutput(index) : null;
    }
    private void validateScriptState() throws Exception {
        List<String> hashes = new ArrayList<>(histories.keySet());
        Set<String> spends = new HashSet<>();
        for (Transaction tx : transactions.values()) for (TransactionInput input : tx.getInputs()) {
            if (!tx.isCoinBase() && !spends.add(input.getOutpoint().toString())) throw new IOException("Conflicting transactions reported; spending disabled");
            TransactionOutput connected = parent(input);
            if (connected != null) input.getScriptSig().correctlySpends(tx,tx.getInputs().indexOf(input),input.getWitness(),
                    connected.getValue(),connected.getScriptPubKey(),Script.ALL_VERIFY_FLAGS);
        }
        for (int start = 0; start < hashes.size(); start += BATCH) {
            checkActive(); List<String> group = hashes.subList(start, Math.min(hashes.size(), start+BATCH));
            List<JSONArray> params = new ArrayList<>(); for (String hash : group) params.add(new JSONArray().put(hash));
            List<Object> unspent = primary.batch("blockchain.scripthash.listunspent", params);
            List<Object> balances = primary.batch("blockchain.scripthash.get_balance", params);
            for (int i = 0; i < group.size(); i++) {
                String hash = group.get(i); Map<String,Long> expected = new TreeMap<>(); long confirmed = 0, pending = 0;
                JSONArray history = histories.get(hash);
                for (int n = 0; n < history.length(); n++) {
                    String id = history.getJSONObject(n).getString("tx_hash").toLowerCase(Locale.ROOT);
                    Transaction tx = transactions.get(id); long delta = 0; boolean relevant = false;
                    for (TransactionOutput output : tx.getOutputs()) if (hash.equals(outputHash(output))) {
                        relevant = true; delta = Math.addExact(delta, output.getValue().value);
                        String outpoint = output.getOutPointFor().toString();
                        if (!spends.contains(outpoint)) expected.put(id + ":" + output.getIndex(), output.getValue().value);
                    }
                    for (TransactionInput input : tx.getInputs()) {
                        TransactionOutput output = parent(input);
                        if (output != null && hash.equals(outputHash(output))) { relevant = true; delta = Math.subtractExact(delta, output.getValue().value); }
                    }
                    if (!relevant) throw new IOException("Server returned unrelated transaction history");
                    if (heights.get(id) > 0) confirmed = Math.addExact(confirmed, delta); else pending = Math.addExact(pending, delta);
                }
                if (!(unspent.get(i) instanceof JSONArray) || !(balances.get(i) instanceof JSONObject)) throw new IOException("Invalid script state");
                Map<String,Long> reported = new TreeMap<>(); JSONArray entries = (JSONArray)unspent.get(i);
                for (int n = 0; n < entries.length(); n++) {
                    JSONObject entry = entries.getJSONObject(n); String id = ElectrumProof.txid(entry.getString("tx_hash"));
                    long index = entry.getLong("tx_pos"), value = entry.getLong("value");
                    Integer height = heights.get(id);
                    if (index < 0 || index > Integer.MAX_VALUE || value < 0 || height == null || entry.getInt("height") != Math.max(0,height)
                            || reported.put(id + ":" + index, value) != null) throw new IOException("Invalid UTXO");
                }
                JSONObject balance = (JSONObject)balances.get(i);
                if (!expected.equals(reported) || confirmed != balance.getLong("confirmed") || pending != balance.getLong("unconfirmed"))
                    throw new IOException("Electrum balance/UTXO/history mismatch");
            }
        }
    }
    private void rebuild(Wallet wallet) throws Exception {
        List<String> ordered = new ArrayList<>(transactions.keySet());
        ordered.sort(Comparator.comparingInt(id -> heights.get(id) > 0 ? heights.get(id) : Integer.MAX_VALUE));
        // Topologically order same-block and unconfirmed dependencies.
        List<String> sorted = new ArrayList<>(); Set<String> visiting = new HashSet<>(), done = new HashSet<>();
        for (String id : ordered) visit(id, visiting, done, sorted, 0);
        wallet.reset();
        Map<String,Transaction> confirmedSpends = new HashMap<>();
        for (String id : sorted) {
            Transaction tx = transactions.get(id); int height = heights.get(id);
            if (height > 0) {
                wallet.receiveFromBlock(tx, headers.at(height), AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
                tx.getConfidence().setDepthInBlocks(headers.tip().getHeight()-height+1);
                tx.setUpdateTime(headers.at(height).getHeader().getTime());
            } else wallet.maybeCommitTx(tx);
            for (TransactionInput input : tx.getInputs()) confirmedSpends.put(input.getOutpoint().toString(), tx);
        }
        for (Transaction old : previous.values()) {
            if (old.getConfidence().getSource() != TransactionConfidence.Source.SELF || transactions.containsKey(old.getTxId().toString())) continue;
            // Keep signed local transactions without automatically relaying them.
            Transaction retained = Transaction.read(ByteBuffer.wrap(old.serialize())); retained.setMemo(old.getMemo()); retained.setPurpose(old.getPurpose()); retained.setExchangeRate(old.getExchangeRate()); retained.setUpdateTime(old.getUpdateTime());
            retained.getConfidence().setSource(TransactionConfidence.Source.SELF);
            Transaction overriding = null;
            for (TransactionInput input : retained.getInputs()) if (confirmedSpends.containsKey(input.getOutpoint().toString())) overriding = confirmedSpends.get(input.getOutpoint().toString());
            if (overriding != null) {
                retained.getConfidence().setOverridingTransaction(overriding);
                wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.DEAD, retained));
            } else wallet.maybeCommitTx(retained);
        }
        StoredBlock tip = headers.tip();
        wallet.setLastBlockSeenHash(tip.getHeader().getHash()); wallet.setLastBlockSeenHeight(tip.getHeight()); wallet.setLastBlockSeenTime(java.time.Instant.ofEpochSecond(tip.getHeader().getTimeSeconds()));
        wallet.setTransactionBroadcaster(null);
        wallet.isConsistentOrThrow();
    }
    private void visit(String id, Set<String> visiting, Set<String> done, List<String> sorted, int depth) throws IOException {
        if (done.contains(id)) return;
        if (depth > 1000 || !visiting.add(id)) throw new IOException("Cyclic or excessive transaction dependencies");
        for (TransactionInput input : transactions.get(id).getInputs()) {
            String parent = input.getOutpoint().getHash().toString(); if (transactions.containsKey(parent)) visit(parent, visiting, done, sorted, depth+1);
        }
        visiting.remove(id); done.add(id); sorted.add(id);
    }
}

