package com.example.legacybtc;

// Provides access to Android application storage.
import android.content.Context;

// Provides access to Android's secure hardware-backed key storage system.
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

// Provides byte-array streams so the bitcoinj wallet can be converted
// into bytes without first writing an unencrypted wallet file to disk.
import java.io.ByteArrayOutputStream;

// Provides file output functionality for saving encrypted wallet data.
import java.io.FileOutputStream;

// Provides access to cryptographic keys stored in Android Keystore.
import java.security.KeyStore;

// Generates the AES encryption key used to protect the wallet file.
import javax.crypto.KeyGenerator;

// Performs AES encryption and decryption operations.
import javax.crypto.Cipher;

// Represents the AES secret key stored inside Android Keystore.
import javax.crypto.SecretKey;

// Provides the initialization vector information required by AES-GCM.
import javax.crypto.spec.GCMParameterSpec;

// Imports the bitcoinj Wallet class so wallet data can be serialized.
import org.bitcoinj.wallet.Wallet;

// Provides file input functionality for reading the encrypted wallet file.
import java.io.FileInputStream;

// Provides an in-memory input stream containing the decrypted
// bitcoinj wallet data.
import java.io.ByteArrayInputStream;


/**
 * WalletStorageManager securely stores LegacyBTC wallet data
 * inside the application's private Android storage.
 *
 * The wallet is serialized by bitcoinj, encrypted using AES-GCM,
 * and protected by an encryption key stored in Android Keystore.
 */
public class WalletStorageManager {

    // Name used to identify the LegacyBTC encryption key
    // inside Android Keystore.
    private static final String KEY_ALIAS =
            "LegacyBTCWalletEncryptionKey";

    // Name of the encrypted wallet file stored in the application's
    // private internal storage.
    private static final String WALLET_FILE_NAME =
            "legacybtc_wallet.enc";

    // Identifies Android Keystore as the secure key provider.
    private static final String ANDROID_KEYSTORE =
            "AndroidKeyStore";

    /**
     * Creates the AES encryption key if it does not already exist.
     *
     * The key is generated and stored inside Android Keystore instead
     * of being saved directly in the application's files.
     *
     * @throws Exception if the key cannot be created or accessed
     */
    private static void createEncryptionKeyIfNeeded() throws Exception {

        // Opens Android Keystore so the application can check whether
        // the LegacyBTC encryption key already exists.
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        // Stops here if the encryption key was previously created.
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return;
        }

        // Creates a generator for a 256-bit AES key that will
        // be stored directly inside Android Keystore.
        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                );

        /*
         * Defines how the AES key is allowed to be used.
         *
         * PURPOSE_ENCRYPT and PURPOSE_DECRYPT allow the same key
         * to protect and recover the wallet data.
         *
         * BLOCK_MODE_GCM provides authenticated encryption, meaning
         * modification of the encrypted wallet data can be detected.
         *
         * ENCRYPTION_PADDING_NONE is required when AES-GCM is used.
         */
        KeyGenParameterSpec keySpecification =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(256)
                        .build();

        // Applies the encryption requirements to the key generator.
        keyGenerator.init(keySpecification);

        // Generates the AES key and stores it inside Android Keystore.
        keyGenerator.generateKey();
    }

    /**
     * Encrypts and saves the Bitcoin wallet inside the application's
     * private internal storage.
     *
     * @param context provides access to the application's internal storage
     * @param wallet  the bitcoinj Wallet object that will be protected and saved
     * @throws Exception if serialization, encryption, or file storage fails
     */
    public static void saveWallet(Context context, Wallet wallet) throws Exception {

        // Ensures that the AES encryption key exists before
        // attempting to protect the wallet data.
        createEncryptionKeyIfNeeded();

        // Creates an in-memory output stream that receives the serialized
        // bitcoinj wallet without writing plaintext wallet data to disk.
        ByteArrayOutputStream walletOutputStream =
                new ByteArrayOutputStream();

        // Serializes the complete bitcoinj wallet into the byte-array stream.
        wallet.saveToFileStream(walletOutputStream);

        // Retrieves the serialized wallet as a byte array so it can
        // be encrypted before being written to Android storage.
        byte[] walletBytes =
                walletOutputStream.toByteArray();

        // Opens Android Keystore and loads the existing LegacyBTC AES key.
        KeyStore keyStore =
                KeyStore.getInstance(ANDROID_KEYSTORE);

        keyStore.load(null);

        // Retrieves the AES secret key using the alias assigned
        // when the encryption key was originally generated.
        SecretKey secretKey =
                ((KeyStore.SecretKeyEntry)
                        keyStore.getEntry(KEY_ALIAS, null))
                        .getSecretKey();

        // Creates an AES-GCM cipher for authenticated wallet encryption.
        Cipher cipher =
                Cipher.getInstance("AES/GCM/NoPadding");

        // Initializes the cipher for encryption using the
        // AES key stored inside Android Keystore.
        cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey
        );

        // Encrypts the serialized wallet bytes.
        byte[] encryptedWalletBytes =
                cipher.doFinal(walletBytes);

        // Retrieves the unique initialization vector generated
        // for this AES-GCM encryption operation.
        byte[] initializationVector =
                cipher.getIV();

        /*
         * Opens a private application file.
         *
         * MODE_PRIVATE prevents other applications from directly
         * accessing the encrypted wallet file through normal Android storage.
         */
        FileOutputStream fileOutputStream =
                context.openFileOutput(
                        WALLET_FILE_NAME,
                        Context.MODE_PRIVATE
                );

        /*
         * Stores the initialization vector length first.
         *
         * The IV is not secret, but it is required later when
         * decrypting the wallet.
         */
        fileOutputStream.write(initializationVector.length);

        // Stores the initialization vector.
        fileOutputStream.write(initializationVector);

        // Stores the encrypted wallet data after the initialization vector.
        fileOutputStream.write(encryptedWalletBytes);

        // Closes the file after the encrypted wallet has been written.
        fileOutputStream.close();

        // Closes the in-memory wallet serialization stream.
        walletOutputStream.close();
    }

    /**
     * Loads the previously saved Bitcoin wallet from encrypted
     * application storage.
     *
     * @param context provides access to the application's private storage
     * @return the decrypted and reconstructed bitcoinj Wallet object
     * @throws Exception if the wallet file cannot be read, decrypted, or loaded
     */
    public static Wallet loadWallet(Context context) throws Exception {

        // Opens the encrypted wallet file from the application's
        // private internal storage.
        FileInputStream fileInputStream =
                context.openFileInput(WALLET_FILE_NAME);

        // Reads the first byte, which contains the number of bytes
        // used by the AES-GCM initialization vector.
        int initializationVectorLength =
                fileInputStream.read();

        // Creates an array large enough to store the initialization vector.
        byte[] initializationVector =
                new byte[initializationVectorLength];

        // Reads the initialization vector from the encrypted wallet file.
        fileInputStream.read(initializationVector);

        // Reads the remaining bytes from the file.
        // These bytes contain the encrypted bitcoinj wallet.
        byte[] encryptedWalletBytes =
                fileInputStream.readAllBytes();

        // Closes the wallet file after all encrypted data has been read.
        fileInputStream.close();


        // Opens Android Keystore so the existing LegacyBTC
        // AES encryption key can be retrieved.
        KeyStore keyStore =
                KeyStore.getInstance(ANDROID_KEYSTORE);

        keyStore.load(null);

        // Retrieves the same AES key that was used when
        // the wallet was originally encrypted.
        SecretKey secretKey =
                ((KeyStore.SecretKeyEntry)
                        keyStore.getEntry(KEY_ALIAS, null))
                        .getSecretKey();


        // Creates an AES-GCM cipher for decrypting the wallet.
        Cipher cipher =
                Cipher.getInstance("AES/GCM/NoPadding");

        // Reconstructs the AES-GCM configuration using the
        // initialization vector stored with the encrypted wallet.
        GCMParameterSpec parameterSpec =
                new GCMParameterSpec(
                        128,
                        initializationVector
                );

        // Initializes the cipher for decryption using the
        // Android Keystore AES key and stored initialization vector.
        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                parameterSpec
        );

        // Decrypts the encrypted wallet bytes.
        byte[] walletBytes =
                cipher.doFinal(encryptedWalletBytes);


        // Creates an in-memory input stream containing the original
        // serialized bitcoinj wallet data.
        ByteArrayInputStream walletInputStream =
                new ByteArrayInputStream(walletBytes);

        // Reconstructs the bitcoinj Wallet object from the
        // decrypted serialized wallet data.
        Wallet wallet =
                Wallet.loadFromFileStream(walletInputStream);

        // Closes the in-memory stream after the wallet has been loaded.
        walletInputStream.close();

        // Returns the reconstructed Bitcoin wallet to the caller.
        return wallet;
    }

}