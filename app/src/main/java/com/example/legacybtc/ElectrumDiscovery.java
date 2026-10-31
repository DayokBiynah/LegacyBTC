package com.example.legacybtc;

import org.bitcoinj.base.Address;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single-address diagnostic lookup. Normal wallet reconciliation uses ElectrumSync;
 * this helper's reported values must never authorize spending.
 */
final class ElectrumDiscovery {
    static final class Snapshot {
        final long reportedConfirmedSatoshis;
        final long reportedUnconfirmedSatoshis;
        final List<String> transactionIds;
        Snapshot(long confirmed, long unconfirmed, List<String> ids) {
            reportedConfirmedSatoshis = confirmed;
            reportedUnconfirmedSatoshis = unconfirmed;
            transactionIds = Collections.unmodifiableList(new ArrayList<>(ids));
        }
    }

    private ElectrumDiscovery() { }

    static Snapshot lookup(Address address, String host, int port) throws Exception {
        String scriptHash = ElectrumClient.scriptHash(address);
        try (ElectrumClient client = new ElectrumClient(host, port)) {
            JSONObject balance = client.getBalance(scriptHash);
            JSONArray history = client.getHistory(scriptHash);
            if (history.length() > 100000) throw new IOException("Unreasonable history size");
            List<String> txids = new ArrayList<>();
            for (int i = 0; i < history.length(); i++) {
                String txid = history.getJSONObject(i).getString("tx_hash");
                if (!txid.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid transaction ID");
                txids.add(txid);
            }
            return new Snapshot(balance.getLong("confirmed"), balance.getLong("unconfirmed"), txids);
        }
    }
}
