package com.example.legacybtc;

import org.bitcoinj.base.Sha256Hash;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class ElectrumProof {
    static org.bitcoinj.core.StoredBlock ancestor(org.bitcoinj.core.StoredBlock child, org.bitcoinj.core.Block parent) throws Exception {
        if (child.getHeight() <= 0 || !child.getHeader().getPrevBlockHash().equals(parent.getHash()))
            throw new IOException("Historical header does not link to the trusted chain");
        org.bitcoinj.core.Block.verifyHeader(parent);
        java.math.BigInteger work = child.getChainWork().subtract(child.getHeader().getWork());
        if (work.signum() <= 0) throw new IOException("Invalid historical chain work");
        return new org.bitcoinj.core.StoredBlock(parent,work,child.getHeight()-1);
    }
    static String txid(String value) throws IOException {
        if (value == null || !value.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid transaction ID");
        return value.toLowerCase(java.util.Locale.ROOT);
    }
    static void verifyMerkle(String id, JSONObject proof, int height, Sha256Hash root) throws Exception {
        if (proof.getInt("block_height") != height) throw new IOException("Merkle proof height mismatch");
        long position = proof.getLong("pos"); JSONArray branch = proof.getJSONArray("merkle");
        if (position < 0 || branch.length() > 32) throw new IOException("Invalid Merkle position or depth");
        byte[] hash = Sha256Hash.wrap(txid(id)).getReversedBytes();
        for (int i = 0; i < branch.length(); i++) {
            byte[] other = Sha256Hash.wrap(txid(branch.getString(i))).getReversedBytes(), pair = new byte[64];
            System.arraycopy((position & 1) == 0 ? hash : other, 0, pair, 0, 32);
            System.arraycopy((position & 1) == 0 ? other : hash, 0, pair, 32, 32);
            hash = Sha256Hash.hashTwice(pair); position >>>= 1;
        }
        if (position != 0 || !Sha256Hash.wrapReversed(hash).equals(root)) throw new IOException("Invalid transaction inclusion proof");
    }
    static long feeRate(Object btcPerKb) {
        BigDecimal value = new BigDecimal(btcPerKb.toString());
        if (value.signum() <= 0) throw new IllegalArgumentException("Fee estimate unavailable");
        long rate = value.multiply(BigDecimal.valueOf(100_000)).setScale(0, RoundingMode.CEILING).longValueExact();
        return WalletPolicy.parseFeeRate(Long.toString(rate));
    }
}
