package com.example.legacybtc;

import androidx.test.platform.app.InstrumentationRegistry;
import org.bitcoinj.core.*;
import org.bitcoinj.base.Sha256Hash;
import org.junit.Test;
import java.io.File;
import java.math.BigInteger;
import static org.junit.Assert.*;

/** Storage atomicity only. Synthetic rows are not claimed to be valid Bitcoin headers. */
public class HeaderStoreIntegrationTest {
    @Test public void batchedTipSurvivesReopenAndUncommittedBatchRollsBack() throws Exception {
        File database=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"headers-fixture-"+java.util.UUID.randomUUID()+".db");
        ElectrumHeaderStore store=new ElectrumHeaderStore(database);
        try {
            StoredBlock genesis=store.getChainHead();
            StoredBlock next=new StoredBlock(new Block(1,genesis.getHeader().getHash(),Sha256Hash.of(new byte[]{11}),java.time.Instant.now(),0x1d00ffffL,1,null),BigInteger.TEN,1);
            store.begin(); try { store.put(next); store.setChainHead(next); store.commit(); } finally { store.end(); }
            store.close(); store=new ElectrumHeaderStore(database);
            assertEquals(next.getHeader().getHash(),store.at(1).getHeader().getHash());
            StoredBlock failed=new StoredBlock(new Block(1,next.getHeader().getHash(),Sha256Hash.of(new byte[]{12}),java.time.Instant.now(),0x1d00ffffL,2,null),BigInteger.valueOf(20),2);
            store.begin(); try { store.put(failed); store.setChainHead(failed); } finally { store.end(); }
            assertEquals(1,store.getChainHead().getHeight()); assertNull(store.get(failed.getHeader().getHash()));
            store.close(); store=new ElectrumHeaderStore(database); assertEquals(1,store.getChainHead().getHeight());
        } finally { store.close(); android.database.sqlite.SQLiteDatabase.deleteDatabase(database); }
    }
}
