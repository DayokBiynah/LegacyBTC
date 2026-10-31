package com.example.legacybtc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared public headers; a batch and its canonical tip commit atomically. */
final class ElectrumHeaderStore implements BlockStore {
    private final SQLiteDatabase db;
    private StoredBlock head;
    private StoredBlock beforeBatch;
    private boolean inBatch, committed;
    private StoredBlock checkpoint;
    private final Map<Sha256Hash,StoredBlock> pending = new LinkedHashMap<>();
    private final Map<Integer,String> canonical = new LinkedHashMap<>();
    private final Map<Sha256Hash, StoredBlock> recent = new LinkedHashMap<Sha256Hash, StoredBlock>(8192, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Sha256Hash, StoredBlock> entry) { return size() > 8192; }
    };
    ElectrumHeaderStore(Context context) throws Exception {
        this(new java.io.File(context.getFilesDir(), "electrum-headers-" + WalletPolicy.NETWORK_ID + ".db"),true);
    }
    ElectrumHeaderStore(java.io.File database) throws Exception {
        this(database,false);
    }
    private ElectrumHeaderStore(java.io.File database, boolean bootstrap) throws Exception {
        db = SQLiteDatabase.openOrCreateDatabase(database, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS headers(hash TEXT PRIMARY KEY, height INTEGER NOT NULL, work TEXT NOT NULL, bytes BLOB NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS canonical(height INTEGER PRIMARY KEY, hash TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tip(hash TEXT)");
        try (Cursor cursor = db.rawQuery("SELECT hash FROM tip LIMIT 1", null)) {
            if (cursor.moveToFirst()) head = get(Sha256Hash.wrap(cursor.getString(0)));
        }
        if (head == null) {
            Block genesis = parameters().getGenesisBlock(); head = new StoredBlock(genesis, genesis.getWork(), 0);
            begin(); try { put(head); setChainHead(head); commit(); } finally { end(); }
        }
        if (bootstrap && !BuildConfig.REGTEST) {
            try (java.io.InputStream input = org.bitcoinj.core.CheckpointManager.openStream(parameters())) {
                if (input != null) checkpoint = new org.bitcoinj.core.CheckpointManager(parameters(),input)
                        .getCheckpointBefore(java.time.Instant.now().minusSeconds(7*86400));
            }
            if (checkpoint != null && head.getHeight() == 0 && checkpoint.getHeight() > 0) {
                begin(); try { put(checkpoint); setChainHead(checkpoint); commit(); } finally { end(); }
            }
        }
    }
    static org.bitcoinj.core.NetworkParameters parameters() {
        return BuildConfig.REGTEST ? org.bitcoinj.params.RegTestParams.get() : BuildConfig.MAINNET ? org.bitcoinj.params.MainNetParams.get() : org.bitcoinj.params.TestNet3Params.get();
    }
    synchronized void begin() {
        db.beginTransaction(); beforeBatch = head; inBatch = true; committed = false; pending.clear(); canonical.clear();
    }
    synchronized void commit() {
        try (SQLiteStatement insert = db.compileStatement("INSERT OR REPLACE INTO headers(hash,height,work,bytes) VALUES(?,?,?,?)");
             SQLiteStatement index = db.compileStatement("INSERT OR REPLACE INTO canonical(height,hash) VALUES(?,?)")) {
            for (StoredBlock block : pending.values()) {
                insert.bindString(1,block.getHeader().getHash().toString()); insert.bindLong(2,block.getHeight());
                insert.bindString(3,block.getChainWork().toString(16)); insert.bindBlob(4,block.getHeader().serialize()); insert.executeInsert();
            }
            db.delete("canonical","height>?",new String[]{Integer.toString(head.getHeight())});
            for (Map.Entry<Integer,String> entry : canonical.entrySet()) {
                index.bindLong(1,entry.getKey()); index.bindString(2,entry.getValue()); index.executeInsert();
            }
            db.delete("tip",null,null);
            ContentValues tip = new ContentValues(); tip.put("hash",head.getHeader().getHash().toString()); db.insertOrThrow("tip",null,tip);
        }
        db.setTransactionSuccessful(); committed = true;
    }
    synchronized void end() {
        try { db.endTransaction(); }
        finally {
            if (!committed) { head = beforeBatch; recent.clear(); }
            inBatch = false; pending.clear(); canonical.clear();
        }
    }
    @Override public synchronized void put(StoredBlock block) {
        if (inBatch) { pending.put(block.getHeader().getHash(),block); recent.put(block.getHeader().getHash(),block); return; }
        ContentValues values = new ContentValues();
        values.put("hash", block.getHeader().getHash().toString()); values.put("height", block.getHeight());
        values.put("work", block.getChainWork().toString(16)); values.put("bytes", block.getHeader().serialize());
        db.insertWithOnConflict("headers", null, values, SQLiteDatabase.CONFLICT_REPLACE); recent.put(block.getHeader().getHash(), block);
    }
    @Override public synchronized StoredBlock get(Sha256Hash hash) {
        StoredBlock staged = pending.get(hash); if (staged != null) return staged;
        StoredBlock cached = recent.get(hash); if (cached != null) return cached;
        try (Cursor cursor = db.rawQuery("SELECT height,work,bytes FROM headers WHERE hash=?", new String[]{hash.toString()})) {
            if (!cursor.moveToFirst()) return null;
            StoredBlock result = new StoredBlock(Block.read(ByteBuffer.wrap(cursor.getBlob(2))), new BigInteger(cursor.getString(1),16), cursor.getInt(0));
            recent.put(hash, result); return result;
        }
    }
    synchronized StoredBlock at(int height) throws BlockStoreException {
        try (Cursor cursor = db.rawQuery("SELECT hash FROM canonical WHERE height=?", new String[]{Integer.toString(height)})) {
            if (!cursor.moveToFirst()) throw new BlockStoreException("Missing verified header " + height);
            return get(Sha256Hash.wrap(cursor.getString(0)));
        }
    }
    synchronized StoredBlock nearestAbove(int height) throws BlockStoreException {
        try (Cursor cursor = db.rawQuery("SELECT height FROM canonical WHERE height>=? ORDER BY height LIMIT 1",new String[]{Integer.toString(height)})) {
            if (!cursor.moveToFirst()) throw new BlockStoreException("No trusted header above requested height");
            return at(cursor.getInt(0));
        }
    }
    synchronized void putAncestor(StoredBlock ancestor) {
        if (!inBatch) throw new IllegalStateException("Ancestor writes require a transaction");
        put(ancestor); canonical.put(ancestor.getHeight(),ancestor.getHeader().getHash().toString());
    }
    void enforceCheckpoint(org.bitcoinj.core.Block block, int height) throws java.io.IOException {
        if (checkpoint != null && height == checkpoint.getHeight() && !block.getHash().equals(checkpoint.getHeader().getHash()))
            throw new java.io.IOException("Header conflicts with the bundled trusted checkpoint");
    }
    @Override public synchronized StoredBlock getChainHead() { return head; }
    @Override public synchronized void setChainHead(StoredBlock block) {
        if (!inBatch) throw new IllegalStateException("Header tip updates require a transaction");
        if (head != null && block.getHeight() == head.getHeight()+1 && block.getHeader().getPrevBlockHash().equals(head.getHeader().getHash())) {
            canonical.put(block.getHeight(),block.getHeader().getHash().toString()); head = block; return;
        }
        StoredBlock cursor = block;
        while (cursor != null) {
            String hash = cursor.getHeader().getHash().toString();
            if (hash.equals(canonical.get(cursor.getHeight()))) break;
            try (Cursor known = db.rawQuery("SELECT hash FROM canonical WHERE height=?", new String[]{Integer.toString(cursor.getHeight())})) {
                if (known.moveToFirst() && known.getString(0).equals(hash)) break;
            }
            canonical.put(cursor.getHeight(),hash); cursor = get(cursor.getHeader().getPrevBlockHash());
        }
        head = block;
    }
    @Override public synchronized void close() { db.close(); recent.clear(); }
}
