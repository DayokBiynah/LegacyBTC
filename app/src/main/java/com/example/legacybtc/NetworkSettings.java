package com.example.legacybtc;

/**
 * Validates TLS Electrum host/port input. Network identity is checked using server genesis,
 * not inferred from a port number. Legacy Bitcoin P2P ports are rejected.
 */
public final class NetworkSettings {
    private final String host;
    private final int port;
    public NetworkSettings(String host, String port) {
        this.host = host.trim();
        if (!this.host.isEmpty() && (!this.host.matches("[a-zA-Z0-9][a-zA-Z0-9.-]{0,252}") || this.host.contains("..")))
            throw new IllegalArgumentException("Use a TLS Electrum hostname or IPv4 address, without a URL or spaces.");
        int number;
        try { number = Integer.parseInt(port.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a TLS Electrum port."); }
        if (number < 1 || number > 65535 || number == 18333 || number == 8333)
            throw new IllegalArgumentException("Use a TLS Electrum port from 1–65535, rather than a Bitcoin P2P port.");
        this.port = number;
    }
    public String getHost() { return host; }
    public int getPort() { return port; }
}
