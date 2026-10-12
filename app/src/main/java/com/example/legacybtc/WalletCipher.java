package com.example.legacybtc;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * I use Java's standard AES/GCM/NoPadding implementation to protect serialized wallet and plan
 * bytes. The envelope stores a twelve-byte IV followed by ciphertext and the authentication
 * tag. Encryption generates a fresh IV through the provider. Decryption rejects tampering or a
 * wrong key instead of returning untrusted plaintext. I preserve the existing format and do
 * not implement custom cryptography.
 */
public final class WalletCipher {
    private WalletCipher() { }
    /**
     * I initialize Java Cipher in AES-GCM encryption mode with the supplied SecretKey. The
     * provider generates an IV and doFinal produces authenticated ciphertext. I assemble the
     * existing IV-length/IV/ciphertext envelope and return bytes for atomic storage.
     * Cryptographic failures propagate without a partially accepted wallet.
     * @param plain byte[] supplied by the calling workflow as described above
     * @param key SecretKey supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return the IV and authenticated ciphertext envelope for atomic storage
     */
    public static byte[] encrypt(byte[] plain, SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(plain);
        byte[] result = new byte[1 + iv.length + encrypted.length];
        result[0] = (byte) iv.length;
        System.arraycopy(iv, 0, result, 1, iv.length);
        System.arraycopy(encrypted, 0, result, 1 + iv.length, encrypted.length);
        return result;
    }
    /**
     * I validate the envelope length and twelve-byte IV, initialize Java Cipher with a 128-bit
     * GCM authentication tag, and call doFinal on the ciphertext. Wrong keys, truncation, or
     * tampering raise GeneralSecurityException. Only authenticated plaintext is returned to
     * wallet or plan parsing.
     * @param data byte[] supplied by the calling workflow as described above
     * @param key SecretKey supplied by the calling workflow as described above
     * @throws Exception if the validation, library, or storage operation described above fails
     * @return plaintext only after successful GCM authentication
     */
    public static byte[] decrypt(byte[] data, SecretKey key) throws GeneralSecurityException {
        if (data.length < 29 || (data[0] & 255) != 12)
            throw new GeneralSecurityException("Invalid encrypted wallet format.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(data, 1, 13)));
        return cipher.doFinal(data, 13, data.length - 13);
    }
}
