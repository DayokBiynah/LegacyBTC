package com.example.legacybtc;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.uri.BitcoinURI;

/**
 * I accept a public recipient address or the bitcoin: URI text decoded by a QR reader.
 * bitcoinj parses BIP-21 syntax and required parameters. I never fetch payment-request
 * URLs or submit a transaction from this input; the ordinary review still shows the
 * actual destination, amount, fee, and network before authentication.
 */
public final class PaymentInput {
    private final Address address;
    private final Coin amount;
    private PaymentInput(Address address, Coin amount) { this.address = address; this.amount = amount; }

    /**
     * This method receives recipient and amount Strings captured from the Send fields.
     * For a URI, BitcoinURI.of(text, NETWORK) validates its network and BIP-21 fields.
     * I reject addressless requests and a URI amount that disagrees with a manually
     * entered amount, rather than silently changing what the user intends to pay.
     * WalletPolicy.parseAmount checks positive values and exact satoshi precision.
     * @param recipient address or externally decoded Bitcoin QR URI
     * @param amountText manually entered BTC, or empty when the URI supplies it
     * @return validated public destination and exact amount for ordinary payment review
     * @throws Exception if URI parsing, network, or amount validation fails
     */
    public static PaymentInput parse(String recipient, String amountText) throws Exception {
        String text = recipient.trim();
        if (!text.toLowerCase(java.util.Locale.ROOT).startsWith("bitcoin:"))
            return new PaymentInput(WalletPolicy.validateAddress(text), WalletPolicy.parseAmount(amountText));
        BitcoinURI uri = BitcoinURI.of("bitcoin:" + text.substring(8), WalletPolicy.NETWORK);
        if (uri.getAddress() == null) throw new IllegalArgumentException("The Bitcoin URI must contain a recipient address.");
        Coin manual = amountText.trim().isEmpty() ? null : WalletPolicy.parseAmount(amountText);
        Coin requested = uri.getAmount();
        if (manual != null && requested != null && !manual.equals(requested))
            throw new IllegalArgumentException("The entered amount differs from the Bitcoin URI. Review and correct the amount.");
        Coin value = manual != null ? manual : requested;
        if (value == null) throw new IllegalArgumentException("Enter the BTC amount.");
        value = WalletPolicy.parseAmount(value.toPlainString());
        return new PaymentInput(uri.getAddress(), value);
    }
    /** @return public recipient address for the reviewed output */
    public String getAddress() { return address.toString(); }
    /** @return exact BTC decimal text without floating-point conversion */
    public String getAmount() { return amount.toPlainString(); }
}
