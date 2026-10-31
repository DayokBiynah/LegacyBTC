package com.example.legacybtc;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkSettingsTest {
    @Test public void testnetDiscoveryAndManualPeerInputsAreValidated() {
        String defaultPort = Integer.toString(WalletPolicy.DEFAULT_PORT);
        assertEquals("", new NetworkSettings("", defaultPort).getHost());
        assertEquals("192.0.2.1", new NetworkSettings("192.0.2.1", defaultPort).getHost());
        for (String host : new String[]{"https://peer.example", "a b", "a..b", "a@b"})
            assertThrows(IllegalArgumentException.class, () -> new NetworkSettings(host, "18333"));
        for (String port : new String[]{"0", "65536", Integer.toString(BuildConfig.MAINNET ? 18333 : 8333), "port"})
            assertThrows(IllegalArgumentException.class, () -> new NetworkSettings("peer.example", port));
    }
}
