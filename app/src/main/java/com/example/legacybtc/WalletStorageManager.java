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

public final class WalletStorageManager {
    private static final String KEY_ALIAS = "LegacyBTCWalletEncryptionKey";
    // Keeps the existing Testnet names readable. Mainnet gets a distinct prefix; neither
    // build migrates, converts, or lists the other network's encrypted wallet files.
    private static final String PREFIX = BuildConfig.REGTEST ? "legacybtc_wallet_regtest_" : BuildConfig.MAINNET ? "legacybtc_wallet_mainnet_" : "legacybtc_wallet_", EXTENSION = ".enc";
    private WalletStorageManager() { }
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
    public static void validateFileName(String name) {
        if (name == null || !name.matches(java.util.regex.Pattern.quote(PREFIX) + "[a-zA-Z0-9-]+\\.enc"))
            throw new IllegalArgumentException("Invalid wallet file name.");
    }
    public static synchronized String findWallet(Context context, Wallet wallet) throws Exception {
        String identity = WalletPolicy.walletIdentity(wallet);
        for (String file : getSavedWalletFiles(context)) {
            try { if (identity.equals(WalletPolicy.walletIdentity(loadWallet(context, file)))) return file; }
            catch (Exception ignored) { /* A damaged entry does not prevent searching other wallets. */ }
        }
        return null;
    }
    public static synchronized String saveWallet(Context context, Wallet wallet) throws Exception {
        String existing = findWallet(context, wallet);
        if (existing != null) return existing;
        String file = PREFIX + UUID.randomUUID() + EXTENSION;
        String[] savedIdentities = context.getFilesDir().list((dir,name) -> name.startsWith("identity_"));
        if (savedIdentities != null) for (String identityFile : savedIdentities) {
            String candidate = identityFile.substring("identity_".length());
            try {
                validateFileName(candidate);
                if (!new File(context.getFilesDir(),candidate).exists() && !new File(context.getFilesDir(),candidate + ".bak").exists()
                        && WalletPolicy.walletIdentity(wallet).equals(new String(readEncrypted(context,identityFile),java.nio.charset.StandardCharsets.UTF_8))) {
                    file = candidate; break;
                }
            } catch (Exception unreadableIdentity) { /* Never overwrite an existing or unidentifiable wallet. */ }
        }
        updateWallet(context, file, wallet);
        // Recover retained legacy plans whose old wallet was deleted before identity tombstones existed.
        String[] planFiles = context.getFilesDir().list((dir,name) -> name.startsWith("inheritance_") && name.endsWith(".enc"));
        InheritanceRepository plans = new InheritanceRepository(context);
        if (planFiles != null) for (String planFile : planFiles) {
            String oldFile = planFile.substring("inheritance_".length());
            if (oldFile.equals(file)) continue;
            try {
                validateFileName(oldFile);
                if (new File(context.getFilesDir(),oldFile).exists() || new File(context.getFilesDir(),oldFile + ".bak").exists()) continue;
                for (InheritancePlan plan : plans.getPlans(oldFile)) {
                    try { plan.userFor(wallet); } catch (IllegalArgumentException unrelated) { continue; }
                    boolean exists = false;
                    for (InheritancePlan current : plans.getPlans(file)) if (current.getId().equals(plan.getId())) exists = true;
                    if (!exists) plans.save(file,plan);
                }
            } catch (Exception unreadablePlan) { /* Leave original metadata intact for package recovery. */ }
        }
        return file;
    }
    public static synchronized void updateWallet(Context context, String file, Wallet wallet) throws Exception {
        WalletPolicy.requireNetwork(wallet);
        validateFileName(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try { wallet.saveToFileStream(output); }
        catch (Exception error) { WalletDiagnostics.failure("wallet persistence: protobuf write", error); throw error; }
        byte[] plain = output.toByteArray();
        try {
            // Validate in an isolated confidence table before replacing any encrypted bytes.
            org.bitcoinj.core.Context original = org.bitcoinj.core.Context.get();
            try {
                org.bitcoinj.core.Context.propagate(new org.bitcoinj.core.Context());
                Wallet checked = Wallet.loadFromFileStream(new ByteArrayInputStream(plain));
                WalletPolicy.requireNetwork(checked);
                checked.isConsistentOrThrow();
            } catch (Exception error) {
                WalletDiagnostics.failure("wallet persistence: protobuf validation before atomic write", error);
                throw error;
            } finally { org.bitcoinj.core.Context.propagate(original); }
            writeEncrypted(context, file, plain);
        }
        finally { Arrays.fill(plain, (byte) 0); }
    }
    static synchronized void writeEncrypted(Context context, String file, byte[] plain) throws Exception {
        if (plain.length > 128 * 1024 * 1024 - 29)
            throw new IllegalStateException("Wallet data exceeds the 128 MiB storage limit; the previous file is retained.");
        byte[] data = WalletCipher.encrypt(plain, getKey(true));
        AtomicFile atomic = new AtomicFile(new File(context.getFilesDir(), file));
        FileOutputStream stream = null;
        try { stream = atomic.startWrite(); stream.write(data); atomic.finishWrite(stream); }
        catch (Exception error) { if (stream != null) atomic.failWrite(stream); WalletDiagnostics.failure("encrypted storage: atomic write", error); throw error; }
    }
    static synchronized byte[] readEncrypted(Context context, String file) throws Exception {
        AtomicFile atomic = new AtomicFile(new File(context.getFilesDir(), file));
        if (Math.max(atomic.getBaseFile().length(), new File(context.getFilesDir(), file + ".bak").length()) > 128 * 1024 * 1024)
            throw new IllegalStateException("Wallet file exceeds the supported size.");
        return WalletCipher.decrypt(atomic.readFully(), getKey(false));
    }
    public static synchronized Wallet loadWallet(Context context, String file) throws Exception {
        validateFileName(file);
        byte[] plain;
        try { plain = readEncrypted(context, file); }
        catch (Exception error) { WalletDiagnostics.failure("encrypted storage: read/decrypt", error); throw error; }
        try {
            Wallet wallet = Wallet.loadFromFileStream(new ByteArrayInputStream(plain));
            WalletPolicy.requireNetwork(wallet);
            return wallet;
        } catch (Exception error) { WalletDiagnostics.failure("encrypted storage: protobuf reload", error); throw error; }
        finally { Arrays.fill(plain, (byte) 0); }
    }
    public static String[] getSavedWalletFiles(Context context) {
        String[] files = context.getFilesDir().list();
        if (files == null) return new String[0];
        java.util.TreeSet<String> recovered = new java.util.TreeSet<>();
        for (String name : files) {
            String base = name.endsWith(".bak") ? name.substring(0, name.length() - 4) : name;
            try { validateFileName(base); recovered.add(base); }
            catch (IllegalArgumentException ignored) { }
        }
        return recovered.toArray(new String[0]);
    }
    public static synchronized boolean deleteWallet(Context context, String file) throws Exception {
        validateFileName(file);
        if (new File(context.getFilesDir(),file).exists() || new File(context.getFilesDir(),file + ".bak").exists()) {
            String identity = null;
            try {
                identity = WalletPolicy.walletIdentity(loadWallet(context,file));
            } catch (Exception unreadable) { /* Deletion of corrupt data was explicitly confirmed. */ }
            if (identity != null) writeEncrypted(context,"identity_" + file,identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        AtomicFile atomic = new AtomicFile(new File(context.getFilesDir(), file));
        atomic.delete();
        new AtomicFile(new File(context.getFilesDir(),file + ".before-electrum")).delete();
        new AtomicFile(new File(context.getFilesDir(),"electrum_" + file)).delete();
        return !atomic.getBaseFile().exists();
    }
    public static boolean walletAlreadySaved(Context context, Wallet wallet) {
        try { return findWallet(context, wallet) != null; } catch (Exception error) { return false; }
    }
}


