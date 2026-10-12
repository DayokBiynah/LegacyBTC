package com.example.legacybtc;

/**
 * I validate public peer configuration as a hostname or IPv4 address and an integer TCP port.
 * URLs, spaces, malformed names, and the other Bitcoin network's default port are rejected.
 * The port alone does not establish network identity; bitcoinj's selected parameters also
 * determine the handshake and chain rules.
 */
public final class NetworkSettings {
    private final String host;
    private final int port;
    public NetworkSettings(String host, String port) {
        this.host = host.trim();
        if (!this.host.isEmpty() && (!this.host.matches("[a-zA-Z0-9][a-zA-Z0-9.-]{0,252}") || this.host.contains("..")))
            throw new IllegalArgumentException("Use a peer hostname or IPv4 address, without a URL or spaces.");
        int number;
        try { number = Integer.parseInt(port.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a Bitcoin peer port."); }
        if (number < 1 || number > 65535 || number == (BuildConfig.MAINNET ? 18333 : 8333))
            throw new IllegalArgumentException("Use a " + WalletPolicy.NETWORK_LABEL + " peer port from 1–65535; the other network's default port is disabled.");
        this.port = number;
    }
    public String getHost() { return host; }
    public int getPort() { return port; }
}
