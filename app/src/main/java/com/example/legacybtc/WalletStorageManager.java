package com.example.legacybtc;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.bitcoinj.wallet.Wallet;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.UUID;

/**
 * I preserve multiple bitcoinj wallets as authenticated ciphertext in Android private storage.
 * Android Keystore supplies the device-bound AES key and AtomicFile retains the previous file
 * if replacement fails. Mainnet uses a distinct filename prefix; the existing Testnet format
 * remains readable without converting any wallet. Plain serialized bytes are cleared after
 * loading or saving, although Java and bitcoinj may retain their own in-memory copies.
 */
public final class WalletStorageManager {
    private static final String KEY_ALIAS = "LegacyBTCWalletEncryptionKey";
    // I keep the existing Testnet names readable. Mainnet gets a distinct prefix; neither
    // build migrates, converts, or lists the other network's encrypted wallet files.
    private static final String PREFIX = BuildConfig.MAINNET ? "legacybtc_wallet_mainnet_" : "legacybtc_wallet_", EXTENSION = ".enc";
    private WalletStorageManager() { }
    /**
     * I open AndroidKeyStore and retrieve the application AES key. When creating a new
     * envelope, a missing alias generates AES-256 with GCM through the platform KeyGenerator.
     * Loading never creates a replacement for a missing key because it could not decrypt old
     * files; the caller must restore from recovery information.
     * @param create boolean supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the device-bound AES SecretKey supplied by Android Keystore
     */
    private static SecretKey getKey(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            if (!create) throw new IllegalStateException("Encryption key unavailable. Restore from your recovery phrase.");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
    }
    /**
     * I validate a filename against this build's network-specific wallet pattern before any
     * file operation. This prevents directory traversal and opening the other network's
     * namespace. Existing Testnet names retain their format; Mainnet uses its own prefix.
     * @param name String supplied by the calling workflow as described above
     */
    public static void validateFileName(String name) {
        if (name == null || !name.matches(BuildConfig.MAINNET
                ? "legacybtc_wallet_mainnet_[a-zA-Z0-9-]+\\.enc" : "legacybtc_wallet_[a-zA-Z0-9-]+\\.enc"))
            throw new IllegalArgumentException("Invalid wallet file name.");
    }
    /**
     * I compute the deterministic account identity and loop over saved files from this
     * network. Matching identity reuses the existing wallet rather than overwriting it during
     * restoration. A damaged entry does not stop the search through other entries. No match
     * returns null.
     * @param context Context supplied by the calling workflow as described above
     * @param wallet Wallet supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the existing account filename, or null when no account matches
     */
    public static synchronized String findWallet(Context context, Wallet wallet) throws Exception {
        String identity = WalletPolicy.walletIdentity(wallet);
        for (String file : getSavedWalletFiles(context)) {
            try { if (identity.equals(WalletPolicy.walletIdentity(loadWallet(context, file)))) return file; }
            catch (Exception ignored) { /* A damaged entry does not prevent searching other wallets. */ }
        }
        return null;
    }
    /**
     * I reuse an existing matching account when found; otherwise I create a UUID filename
     * within this network's namespace and encrypt the wallet. The returned String is the
     * navigation/storage identifier, not private key data.
     * @param context Context supplied by the calling workflow as described above
     * @param wallet Wallet supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the existing or newly encrypted wallet filename
     */
    public static synchronized String saveWallet(Context context, Wallet wallet) throws Exception {
        String existing = findWallet(context, wallet);
        if (existing != null) return existing;
        String file = PREFIX + UUID.randomUUID() + EXTENSION;
        updateWallet(context, file, wallet);
        return file;
    }
    /**
     * I validate network and filename, serialize Wallet.saveToFileStream into memory, and pass
     * those bytes to encrypted atomic storage. The plaintext byte array is cleared in a
     * finally block on success or failure. Provider or filesystem exceptions prevent the
     * caller from claiming persistence succeeded.
     * @param context Context supplied by the calling workflow as described above
     * @param file String supplied by the calling workflow as described above
     * @param wallet Wallet supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    public static synchronized void updateWallet(Context context, String file, Wallet wallet) throws Exception {
        WalletPolicy.requireNetwork(wallet);
        validateFileName(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        wallet.saveToFileStream(output);
        byte[] plain = output.toByteArray();
        try { writeEncrypted(context, file, plain); }
        finally { Arrays.fill(plain, (byte) 0); }
    }
    /**
     * I pass serialized bytes and the Android Keystore key to WalletCipher.encrypt.
     * AtomicFile.startWrite writes a replacement; finishWrite publishes it only after success.
     * If writing fails, failWrite restores the previous file and the exception reaches the
     * service.
     * @param context Context supplied by the calling workflow as described above
     * @param file String supplied by the calling workflow as described above
     * @param plain byte[] supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     */
    static synchronized void writeEncrypted(Context context, String file, byte[] plain) throws Exception {
        byte[] data = WalletCipher.encrypt(plain, getKey(true));
        AtomicFile atomic = new AtomicFile(new File(context.getFilesDir(), file));
        FileOutputStream stream = null;
        try { stream = atomic.startWrite(); stream.write(data); atomic.finishWrite(stream); }
        catch (Exception error) { if (stream != null) atomic.failWrite(stream); throw error; }
    }
    /**
     * I reject an oversized file, read the AtomicFile envelope, and ask WalletCipher.decrypt
     * to authenticate and decrypt it with the existing device key. The returned byte array is
     * private serialized data that the caller must clear after parsing. Corruption and
     * missing-key errors propagate.
     * @param context Context supplied by the calling workflow as described above
     * @param file String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return authenticated plaintext bytes that the caller must clear after use
     */
    static synchronized byte[] readEncrypted(Context context, String file) throws Exception {
        AtomicFile atomic = new AtomicFile(new File(context.getFilesDir(), file));
        if (atomic.getBaseFile().length() > 32 * 1024 * 1024)
            throw new IllegalStateException("Wallet file exceeds the supported size.");
        return WalletCipher.decrypt(atomic.readFully(), getKey(false));
    }
    /**
     * I validate the network-specific filename, decrypt the file, and parse its bytes with
     * bitcoinj Wallet.loadFromFileStream. I check the internal wallet network before returning
     * it and clear the plaintext array in finally. Corrupt files and absent Keystore keys
     * raise errors instead of silently creating a replacement wallet.
     * @param context Context supplied by the calling workflow as described above
     * @param file String supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the network-checked parsed Wallet when loading, or no value for the screen-loading callback
     */
    public static synchronized Wallet loadWallet(Context context, String file) throws Exception {
        validateFileName(file);
        byte[] plain = readEncrypted(context, file);
        try {
            Wallet wallet = Wallet.loadFromFileStream(new ByteArrayInputStream(plain));
            WalletPolicy.requireNetwork(wallet);
            return wallet;
        } finally { Arrays.fill(plain, (byte) 0); }
    }
    /**
     * I list only filenames matching this build's wallet namespace and sort the resulting
     * String array. A missing directory listing returns an empty array. Mainnet files are not
     * converted or exposed in Testnet, and legacy Testnet files remain untouched.
     * @param context Context supplied by the calling workflow as described above
     * @return a sorted array of filenames belonging only to this build network
     */
    public static String[] getSavedWalletFiles(Context context) {
        String[] files = context.getFilesDir().list((dir, name) -> name.matches(BuildConfig.MAINNET
                ? "legacybtc_wallet_mainnet_[a-zA-Z0-9-]+\\.enc" : "legacybtc_wallet_[a-zA-Z0-9-]+\\.enc"));
        if (files == null) return new String[0];
        Arrays.sort(files);
        return files;
    }
    /**
     * I receive a validated filename from the authenticated deletion flow. The service closes
     * and saves a selected wallet before the storage helper removes its local AtomicFile. The
     * boolean result reports file removal. This does not remove an inheritance UTXO from the
     * blockchain; the warning requires a separate backup.
     * @param context Context supplied by the calling workflow as described above
     * @param file String supplied by the calling workflow as described above
     * @return true if the selected local wallet file no longer exists
     */
    public static synchronized boolean deleteWallet(Context context, String file) {
        validateFileName(file);
        AtomicFile atomic = new AtomicFile(new File(context.getFilesDir(), file));
        atomic.delete();
        return !atomic.getBaseFile().exists();
    }
    public static boolean walletAlreadySaved(Context context, Wallet wallet) {
        try { return findWallet(context, wallet) != null; } catch (Exception error) { return false; }
    }
}
