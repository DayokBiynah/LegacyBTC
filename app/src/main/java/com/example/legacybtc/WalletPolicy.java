package com.example.legacybtc;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.AddressParser;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import java.util.Arrays;
import java.util.Locale;

/**
 * This class is responsible for shared wallet validation. I select the Bitcoin network
 * at build time so a production wallet cannot silently switch to a testing network.
 * bitcoinj supplies deterministic keys, mnemonic validation, addresses, and coin values;
 * this class adds the application's input and network checks before those values are used.
 */
public final class WalletPolicy {
    public static final BitcoinNetwork NETWORK = BuildConfig.MAINNET ? BitcoinNetwork.MAINNET : BitcoinNetwork.TESTNET;
    public static final String NETWORK_ID = BuildConfig.MAINNET ? "mainnet" : "testnet";
    public static final String NETWORK_LABEL = BuildConfig.MAINNET ? "Mainnet" : "Testnet";
    public static final int DEFAULT_PORT = BuildConfig.MAINNET ? 8333 : 18333;
    public static final String NETWORK_PREFERENCES = NETWORK_ID + "_network";
    // I cap the absolute fee as well as the requested rate to limit accidental overpayment.
    public static final Coin MAX_TRANSACTION_FEE = Coin.valueOf(1_000_000);
    private WalletPolicy() { }
    /**
     * Here, I ask bitcoinj's Wallet.createDeterministic for a new hierarchical wallet.
     * P2WPKH selects native SegWit receiving outputs. bitcoinj generates fresh seed entropy
     * and derives private/public keys; I return the Wallet to the storage service, which
     * encrypts its serialized data before displaying the recovery phrase. This method does
     * not connect to peers or create a Bitcoin transaction.
     * @return a new wallet for this build's network
     */
    public static Wallet createWallet() {
        return Wallet.createDeterministic(NETWORK, ScriptType.P2WPKH);
    }
    /**
     * This method receives recovery words and the original optional BIP-39 passphrase as
     * Strings from the restore fields. I normalize word spacing and case using Locale.ROOT,
     * but preserve the passphrase because changing it derives a different wallet. I pass
     * the words to DeterministicSeed.ofMnemonic and call check() to validate the vocabulary
     * and checksum. Wallet.fromSeed reconstructs this application's P2WPKH account; it
     * does not import arbitrary external derivation paths. The returned Wallet is saved
     * by BitcoinService and later replays the chain to discover its transaction history.
     * @param phrase ordered mnemonic words entered by the user
     * @param passphrase original BIP-39 passphrase, or an empty String
     * @return restored deterministic wallet
     * @throws Exception if the mnemonic is invalid or seed reconstruction fails
     */
    public static Wallet restoreWallet(String phrase, String passphrase) throws Exception {
        String normalized = phrase.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!Arrays.asList(12, 15, 18, 21, 24).contains(normalized.split(" ").length))
            throw new IllegalArgumentException("Use a valid 12, 15, 18, 21, or 24-word recovery phrase.");
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(normalized, passphrase);
        seed.check();
        return Wallet.fromSeed(NETWORK, seed, ScriptType.P2WPKH);
    }
    /**
     * This method receives a String from a recipient or beneficiary address field. I pass
     * its trimmed text to bitcoinj's AddressParser.getDefault(NETWORK).parseAddress.
     * The parser checks the address encoding and selected network and returns an Address
     * usable by SendRequest.to. I translate parsing failures into an input error so the
     * interface stops before constructing a transaction with an invalid destination.
     * @param text public address supplied by the user
     * @return parsed address for this build's network
     * @throws IllegalArgumentException if parsing fails
     */
    public static Address validateAddress(String text) {
        try { return AddressParser.getDefault(NETWORK).parseAddress(text.trim()); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Enter a valid Bitcoin " + NETWORK_LABEL + " address."); }
    }
    /**
     * This method receives decimal BTC text from a payment or inheritance amount field.
     * One BTC contains 100,000,000 satoshis. I reject exponents, negative amounts, and
     * more than eight decimal places, then use Coin.parseCoin to obtain an integer-valued
     * Coin. No floating-point rounding occurs. The positive supply-bounded result becomes
     * a transaction output value; invalid text prevents payment preparation.
     * @param text BTC amount entered by the user
     * @return exact positive satoshi value wrapped in Coin
     * @throws IllegalArgumentException for invalid precision, zero, or excessive values
     */
    public static Coin parseAmount(String text) {
        String amount = text.trim();
        if (!amount.matches("[0-9]+(\\.[0-9]{1,8})?"))
            throw new IllegalArgumentException("Enter BTC with at most eight decimal places.");
        Coin value;
        try { value = Coin.parseCoin(amount); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid Bitcoin amount."); }
        if (!value.isPositive() || value.isGreaterThan(Coin.COIN.multiply(21_000_000)))
            throw new IllegalArgumentException("The amount must be positive and within Bitcoin's supply.");
        return value;
    }
    /**
     * I parse a whole-number satoshi-per-virtual-byte String supplied by the fee input and
     * enforce 1 through 1000. The returned long drives ordinary fee calculation or the
     * conservative inheritance spend estimate. Non-numeric, zero, negative, and excessive
     * rates raise an input error before signing.
     * @param text String supplied by the calling workflow as described above
     * @return a fee rate from 1 through 1000 satoshis per virtual byte
     */
    public static long parseFeeRate(String text) {
        try { long rate = Long.parseLong(text.trim()); if (rate >= 1 && rate <= 1000) return rate; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Use a fee rate from 1 to 1000 sat/vB.");
    }
    /**
     * I compare Wallet.network with this build's fixed BitcoinNetwork. The application never
     * converts wallet data between networks. A mismatch raises IllegalArgumentException before
     * addresses, balances, storage, or signing use that wallet.
     * @param wallet Wallet supplied by the calling workflow as described above
     */
    public static void requireNetwork(Wallet wallet) {
        if (wallet.network() != NETWORK) throw new IllegalArgumentException("Open a " + NETWORK_LABEL + " wallet in this build.");
    }
    /**
     * I serialize the wallet's account watching key for the selected network. This public
     * account identity stays stable when receiving addresses rotate, so restoration can detect
     * a previously saved wallet without comparing its latest address. I enforce the network
     * before returning the String.
     * @param wallet Wallet supplied by the calling workflow as described above
     * @return the stable network-encoded account watching-key String
     */
    public static String walletIdentity(Wallet wallet) {
        requireNetwork(wallet);
        return wallet.getWatchingKey().serializePubB58(NETWORK);
    }
    /**
     * I return the amount bitcoinj currently selects for ordinary payments as an exact Coin
     * value. Watched inheritance outputs are excluded from payment candidates. Confirmed and
     * unconfirmed output totals are displayed separately because confirmation category and
     * spendability are different questions. The interface formats this result as BTC and
     * preparation checks it before selecting inputs.
     * @param wallet Wallet supplied by the calling workflow as described above
     * @return the spendable ordinary-payment amount as an exact Coin
     */
    public static Coin getBalance(Wallet wallet) {
        requireNetwork(wallet);
        return wallet.getCoinSelector().select(Coin.COIN.multiply(21_000_000),
                wallet.calculateAllSpendCandidates(true, true)).valueGathered;
    }
    /**
     * I loop over unspent, non-watched wallet outputs and group them by their parent
     * transaction's confidence. BUILDING means included in the current chain; PENDING
     * means unconfirmed. These totals describe received UTXOs, whereas getBalance uses
     * bitcoinj's coin selector to decide what is currently spendable. Immature coinbase
     * outputs are excluded. Reorganizations can change confidence and therefore totals.
     * @param wallet opened wallet for this build
     * @param confirmed true for confirmed UTXOs, false for pending UTXOs
     * @return exact total for the requested confirmation category
     */
    public static Coin getOutputBalance(Wallet wallet, boolean confirmed) {
        requireNetwork(wallet);
        Coin total = Coin.ZERO;
        for (org.bitcoinj.core.TransactionOutput output : wallet.calculateAllSpendCandidates(true, true)) {
            org.bitcoinj.core.Transaction parent = output.getParentTransaction();
            if (parent == null) continue;
            org.bitcoinj.core.TransactionConfidence.ConfidenceType type = parent.getConfidence().getConfidenceType();
            if ((confirmed && type == org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING)
                    || (!confirmed && type == org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING))
                total = total.add(output.getValue());
        }
        return total;
    }
}
