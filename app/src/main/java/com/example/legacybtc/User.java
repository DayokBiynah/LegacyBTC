package com.example.legacybtc;

import org.bitcoinj.crypto.ECKey;

/**
 * I define the shared public identity and abstract permissions for an owner or beneficiary.
 * The constructor validates the name, compressed secp256k1 public key, network address, and
 * their P2WPKH relationship. Private fields prevent arbitrary replacement of identity data.
 * The abstract methods provide the interface through which services use dynamic binding;
 * authorization still requires the actual wallet signing key.
 */
public abstract class User {
    private final String name;
    private final String publicKey;
    private final String address;
    protected User(String name, String publicKey, String address) {
        // I validate identity at construction so a saved plan cannot contain an empty name
        // or silently pair a beneficiary address with somebody else's authorization key.
        if (name == null || name.trim().isEmpty() || name.length() > 120)
            throw new IllegalArgumentException("Enter a name with 1–120 characters.");
        ECKey key = parsePublicKey(publicKey);
        // ECKey.fromPublicOnly holds no private signing material. Its derived P2WPKH
        // address must equal the supplied address for this build's network.
        WalletPolicy.validateAddress(address);
        if (!key.toAddress(org.bitcoinj.base.ScriptType.P2WPKH, WalletPolicy.NETWORK).toString().equals(address.trim()))
            throw new IllegalArgumentException("The address must match its " + WalletPolicy.NETWORK_LABEL + " SegWit public key.");
        this.name = name.trim(); this.publicKey = Hex.encode(key.getPubKey()); this.address = address.trim();
    }
    /**
     * I receive compressed public-key hexadecimal text from beneficiary setup or a recovery
     * package. I require 33 bytes beginning with 02 or 03, then use ECKey.fromPublicOnly and
     * validate the secp256k1 point. The returned ECKey contains public information only.
     * Invalid points cannot proceed to script construction.
     * @param text String supplied by the calling workflow as described above
     * @return a validated compressed public-only ECKey
     */
    public static ECKey parsePublicKey(String text) {
        if (text == null || !text.trim().matches("(02|03)[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Enter the 33-byte compressed Bitcoin public key (66 hex characters).");
        try {
            ECKey key = ECKey.fromPublicOnly(Hex.decode(text.trim()));
            if (!key.getPubKeyPoint().isValid() || key.getPubKeyPoint().isInfinity()) throw new IllegalArgumentException();
            return key;
        } catch (RuntimeException e) { throw new IllegalArgumentException("The public key is not a valid Bitcoin curve point."); }
    }
    public String getName() { return name; }
    public String getPublicKey() { return publicKey; }
    public String getAddress() { return address; }
    public abstract String describeRole();
    public abstract boolean canRecover(InheritancePlan plan, InheritancePlan.Status status);
    public abstract boolean canManage(InheritancePlan plan);
}
