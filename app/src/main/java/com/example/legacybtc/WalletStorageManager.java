package com.example.legacybtc;

// Provides access to Android application storage.
import android.content.Context;

// Provides access to Android's secure hardware-backed key storage system.
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

// Provides byte-array streams so bitcoinj wallet data can be
// processed in memory without creating an unencrypted wallet file.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

// Provides file input and output functionality for encrypted wallet files.
import java.io.FileInputStream;
import java.io.FileOutputStream;

// Provides safer structured reading of the initialization vector
// and encrypted wallet bytes from storage.
import java.io.DataInputStream;

// Provides access to cryptographic keys stored in Android Keystore.
import java.security.KeyStore;

// Generates unique identifiers for each wallet saved by LegacyBTC.
import java.util.UUID;

// Generates the AES encryption key used to protect wallet files.
import javax.crypto.KeyGenerator;

// Performs AES encryption and decryption operations.
import javax.crypto.Cipher;

// Represents the AES secret key stored inside Android Keystore.
import javax.crypto.SecretKey;

// Provides the initialization vector information required by AES-GCM.
import javax.crypto.spec.GCMParameterSpec;

// Imports the bitcoinj Wallet class so wallet data can be
// serialized, encrypted, decrypted, and reconstructed.
import org.bitcoinj.wallet.Wallet;


/**
 * WalletStorageManager securely stores and loads LegacyBTC
 * Bitcoin wallets inside the application's private Android storage.
 *
 * Each wallet is assigned a unique identifier and stored in its own
 * encrypted file so multiple wallets can exist without overwriting
 * previously saved wallets.
 *
 * Wallet data is serialized by bitcoinj, encrypted using AES-GCM,
 * and protected by an AES key stored in Android Keystore.
 */
public class WalletStorageManager {

    // Name used to identify the LegacyBTC encryption key
    // inside Android Keystore.
    private static final String KEY_ALIAS =
            "LegacyBTCWalletEncryptionKey";

    // Prefix added to every encrypted wallet filename.
    private static final String WALLET_FILE_PREFIX =
            "legacybtc_wallet_";

    // File extension used for encrypted LegacyBTC wallet files.
    private static final String WALLET_FILE_EXTENSION =
            ".enc";

    // Identifies Android Keystore as the secure key provider.
    private static final String ANDROID_KEYSTORE =
            "AndroidKeyStore";


    /**
     * Creates the AES encryption key if it does not already exist.
     *
     * The key is generated and stored inside Android Keystore instead
     * of being written directly into application storage.
     *
     * @throws Exception if the encryption key cannot be created or accessed
     */
    private static void createEncryptionKeyIfNeeded() throws Exception {

        // Opens Android Keystore so LegacyBTC can determine whether
        // its wallet encryption key already exists.
        KeyStore keyStore =
                KeyStore.getInstance(ANDROID_KEYSTORE);

        keyStore.load(null);

        // Stops here when the encryption key already exists.
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return;
        }

        // Creates a generator for a 256-bit AES encryption key
        // that will be stored inside Android Keystore.
        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                );

        /*
         * Defines the permitted uses and cryptographic properties
         * of the LegacyBTC wallet encryption key.
         *
         * PURPOSE_ENCRYPT and PURPOSE_DECRYPT allow the same key
         * to protect and recover wallet data.
         *
         * GCM provides authenticated encryption so unauthorized
         * modification of encrypted wallet data can be detected.
         */
        KeyGenParameterSpec keySpecification =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
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
     * Creates a unique encrypted filename for a Bitcoin wallet.
     *
     * UUID values make accidental filename collisions extremely unlikely
     * and prevent newly generated wallets from overwriting existing wallets.
     *
     * @return a unique encrypted wallet filename
     */
    private static String createUniqueWalletFileName() {

        // Generates a new universally unique identifier for the wallet.
        String walletId =
                UUID.randomUUID().toString();

        // Constructs the encrypted wallet filename.
        return WALLET_FILE_PREFIX
                + walletId
                + WALLET_FILE_EXTENSION;
    }


    /**
     * Encrypts and saves a Bitcoin wallet inside the application's
     * private internal storage.
     *
     * A new unique encrypted file is created every time this method
     * is called, allowing multiple Bitcoin wallets to be stored.
     *
     * @param context provides access to application storage
     * @param wallet  the bitcoinj Wallet object that will be encrypted
     * @return the unique filename assigned to the saved wallet
     * @throws Exception if serialization, encryption, or storage fails
     */
    public static String saveWallet(
            Context context,
            Wallet wallet
    ) throws Exception {

        // Ensures that the AES encryption key exists before
        // wallet encryption begins.
        createEncryptionKeyIfNeeded();


        // Creates an in-memory stream that receives the serialized
        // bitcoinj wallet without storing plaintext wallet data on disk.
        ByteArrayOutputStream walletOutputStream =
                new ByteArrayOutputStream();

        // Serializes the bitcoinj Wallet object into memory.
        wallet.saveToFileStream(walletOutputStream);

        // Retrieves the serialized wallet bytes for encryption.
        byte[] walletBytes =
                walletOutputStream.toByteArray();


        // Opens Android Keystore so the LegacyBTC AES key
        // can be retrieved.
        KeyStore keyStore =
                KeyStore.getInstance(ANDROID_KEYSTORE);

        keyStore.load(null);

        // Retrieves the AES secret key previously created
        // for LegacyBTC wallet encryption.
        SecretKey secretKey =
                ((KeyStore.SecretKeyEntry)
                        keyStore.getEntry(
                                KEY_ALIAS,
                                null
                        ))
                        .getSecretKey();


        // Creates the authenticated AES-GCM cipher.
        Cipher cipher =
                Cipher.getInstance(
                        "AES/GCM/NoPadding"
                );

        // Initializes the cipher for encryption using
        // the AES key stored in Android Keystore.
        cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey
        );

        // Encrypts the serialized bitcoinj wallet.
        byte[] encryptedWalletBytes =
                cipher.doFinal(walletBytes);

        // Retrieves the unique initialization vector created
        // for this AES-GCM encryption operation.
        byte[] initializationVector =
                cipher.getIV();


        // Creates a new unique encrypted filename for this wallet.
        String walletFileName =
                createUniqueWalletFileName();


        /*
         * Opens a private application file using the unique
         * filename assigned to this Bitcoin wallet.
         *
         * Context.MODE_PRIVATE prevents other applications from
         * directly accessing the file through ordinary storage access.
         */
        FileOutputStream fileOutputStream =
                context.openFileOutput(
                        walletFileName,
                        Context.MODE_PRIVATE
                );


        // Stores the number of bytes used by the initialization vector.
        fileOutputStream.write(
                initializationVector.length
        );

        // Stores the initialization vector required for decryption.
        fileOutputStream.write(
                initializationVector
        );

        // Stores the encrypted bitcoinj wallet data.
        fileOutputStream.write(
                encryptedWalletBytes
        );


        // Closes the encrypted wallet file.
        fileOutputStream.close();

        // Closes the in-memory wallet serialization stream.
        walletOutputStream.close();


        // Returns the unique filename so the application can
        // identify this saved wallet later.
        return walletFileName;
    }


    /**
     * Loads a specific encrypted Bitcoin wallet from application storage.
     *
     * @param context        provides access to application storage
     * @param walletFileName identifies the encrypted wallet file to open
     * @return the decrypted and reconstructed bitcoinj Wallet
     * @throws Exception if reading, decryption, or reconstruction fails
     */
    public static Wallet loadWallet(
            Context context,
            String walletFileName
    ) throws Exception {

        // Opens the requested encrypted wallet file.
        FileInputStream fileInputStream =
                context.openFileInput(
                        walletFileName
                );

        // Wraps the file stream so exact byte counts can be read safely.
        DataInputStream dataInputStream =
                new DataInputStream(
                        fileInputStream
                );


        // Reads the first byte containing the AES-GCM
        // initialization vector length.
        int initializationVectorLength =
                dataInputStream.readUnsignedByte();

        // Rejects an invalid initialization vector length before
        // attempting wallet decryption.
        if (initializationVectorLength <= 0
                || initializationVectorLength > 32) {

            dataInputStream.close();

            throw new IllegalStateException(
                    "Invalid wallet initialization vector."
            );
        }


        // Creates an array capable of storing the entire
        // AES-GCM initialization vector.
        byte[] initializationVector =
                new byte[
                        initializationVectorLength
                        ];

        // Reads the complete initialization vector from storage.
        dataInputStream.readFully(
                initializationVector
        );


        // Creates an in-memory stream for collecting the
        // remaining encrypted wallet bytes.
        ByteArrayOutputStream encryptedOutputStream =
                new ByteArrayOutputStream();

        // Temporary buffer used while reading encrypted wallet data.
        byte[] buffer =
                new byte[4096];

        int bytesRead;

        // Reads the remaining encrypted wallet data until
        // the end of the file is reached.
        while ((bytesRead =
                dataInputStream.read(buffer)) != -1) {

            encryptedOutputStream.write(
                    buffer,
                    0,
                    bytesRead
            );
        }

        // Retrieves the complete encrypted wallet data.
        byte[] encryptedWalletBytes =
                encryptedOutputStream.toByteArray();


        // Closes the encrypted file streams.
        dataInputStream.close();
        encryptedOutputStream.close();


        // Opens Android Keystore so the AES wallet encryption
        // key can be retrieved.
        KeyStore keyStore =
                KeyStore.getInstance(
                        ANDROID_KEYSTORE
                );

        keyStore.load(null);


        // Retrieves the AES secret key used to encrypt
        // LegacyBTC wallet files.
        SecretKey secretKey =
                ((KeyStore.SecretKeyEntry)
                        keyStore.getEntry(
                                KEY_ALIAS,
                                null
                        ))
                        .getSecretKey();


        // Creates an AES-GCM cipher for wallet decryption.
        Cipher cipher =
                Cipher.getInstance(
                        "AES/GCM/NoPadding"
                );


        // Reconstructs the GCM configuration using the
        // initialization vector stored with this wallet.
        GCMParameterSpec parameterSpec =
                new GCMParameterSpec(
                        128,
                        initializationVector
                );


        // Initializes the cipher for wallet decryption.
        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                parameterSpec
        );


        // Decrypts the encrypted wallet bytes.
        byte[] walletBytes =
                cipher.doFinal(
                        encryptedWalletBytes
                );


        // Creates an in-memory stream containing the original
        // serialized bitcoinj wallet data.
        ByteArrayInputStream walletInputStream =
                new ByteArrayInputStream(
                        walletBytes
                );


        // Reconstructs the bitcoinj Wallet object from
        // the decrypted serialized wallet data.
        Wallet wallet =
                Wallet.loadFromFileStream(
                        walletInputStream
                );


        // Closes the decrypted in-memory wallet stream.
        walletInputStream.close();


        // Returns the reconstructed Bitcoin wallet.
        return wallet;
    }


    /**
     * Returns the filenames of all encrypted Bitcoin wallets
     * currently stored by LegacyBTC.
     *
     * @param context provides access to application storage
     * @return an array containing encrypted LegacyBTC wallet filenames
     */
    public static String[] getSavedWalletFiles(
            Context context
    ) {

        // Retrieves every private file currently stored by the application.
        String[] applicationFiles =
                context.fileList();

        // Counts the number of files that match the LegacyBTC
        // encrypted wallet filename format.
        int walletFileCount = 0;

        for (String fileName : applicationFiles) {

            if (fileName.startsWith(
                    WALLET_FILE_PREFIX
            ) && fileName.endsWith(
                    WALLET_FILE_EXTENSION
            )) {

                walletFileCount++;
            }
        }


        // Creates an array sized exactly for the number
        // of encrypted wallet files found.
        String[] walletFiles =
                new String[
                        walletFileCount
                        ];


        // Stores each matching encrypted wallet filename
        // inside the result array.
        int walletIndex = 0;

        for (String fileName : applicationFiles) {

            if (fileName.startsWith(
                    WALLET_FILE_PREFIX
            ) && fileName.endsWith(
                    WALLET_FILE_EXTENSION
            )) {

                walletFiles[
                        walletIndex
                        ] = fileName;

                walletIndex++;
            }
        }


        // Returns all encrypted wallet filenames found
        // in LegacyBTC's private storage.
        return walletFiles;
    }

    /**
     * Deletes a specific encrypted Bitcoin wallet file from
     * LegacyBTC's private application storage.
     *
     * @param context provides access to application storage
     * @param walletFileName identifies the encrypted wallet file to delete
     * @return true if the wallet file was successfully deleted
     */
    public static boolean deleteWallet(
            Context context,
            String walletFileName
    ) {

        // Deletes only the selected encrypted wallet file.
        return context.deleteFile(walletFileName);
    }

}