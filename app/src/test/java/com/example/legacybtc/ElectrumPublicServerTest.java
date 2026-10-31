package com.example.legacybtc;

import org.bitcoinj.base.Sha256Hash;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

/** Opt-in read-only TLS checks. No wallet data or transaction is sent. */
public class ElectrumPublicServerTest {
    @Test public void publicTlsServersAgreeOnNetworkAndRecentHeader() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("legacybtc.electrumSmoke"));
        String[] servers = BuildConfig.MAINNET
                ? new String[]{"electrum.blockstream.info:50002","electrum.jhoenicke.de:50002","e.keff.org:50002","electrum.diynodes.com:50022"}
                : new String[]{"electrum.blockstream.info:60002","blackie.c3-soft.com:57006","testnet.aranguren.org:51002"};
        int success = 0, anchor = -1; Sha256Hash hash = null; Exception last = null;
        for (String endpoint : servers) {
            String[] parts = endpoint.split(":");
            try (ElectrumClient client = new ElectrumClient(parts[0],Integer.parseInt(parts[1]))) {
                client.request("server.version",new JSONArray().put("LegacyBTC-read-only-test").put("1.4"));
                JSONObject features = (JSONObject)client.request("server.features",new JSONArray());
                assertEquals(ElectrumHeaderStore.parameters().getGenesisBlock().getHashAsString(),features.getString("genesis_hash"));
                JSONObject tip = (JSONObject)client.request("blockchain.headers.subscribe",new JSONArray());
                if (anchor < 0) anchor = tip.getInt("height")-6;
                Sha256Hash actual = BitcoinNetworkManager.readHeader(client.request("blockchain.block.header",new JSONArray().put(anchor))).getHash();
                if (hash == null) hash = actual; else assertEquals(hash,actual);
                assertEquals(0,client.getHistory(ElectrumClient.scriptHash(WalletPolicy.createWallet().currentReceiveAddress())).length());
                System.out.println("TLS/network/header check passed: " + endpoint + " at " + anchor);
                if (++success == 2) break;
            } catch (Exception unavailable) { last = unavailable; System.out.println("TLS endpoint unavailable: " + endpoint + " (" + unavailable.getClass().getSimpleName() + ")"); }
        }
        if (success < 2) throw new java.io.IOException("Could not verify two public TLS servers",last);
    }
}
