package com.example.legacybtc;
import org.junit.Test;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class WalletCipherTest {
    @Test public void roundTripUsesFreshIvAndRejectsTampering() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
        SecretKey key = generator.generateKey();
        byte[] plain = new byte[128];
        byte[] data = WalletCipher.encrypt(plain, key);
        assertArrayEquals(plain, WalletCipher.decrypt(data, key));
        assertFalse(Arrays.equals(data, WalletCipher.encrypt(plain, key)));
        data[data.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> WalletCipher.decrypt(data, key));
    }
    @Test public void wrongKeyAndTruncationFail() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        byte[] data = WalletCipher.encrypt(new byte[100], generator.generateKey());
        SecretKey wrong = generator.generateKey();
        assertThrows(GeneralSecurityException.class, () -> WalletCipher.decrypt(data, wrong));
        assertThrows(GeneralSecurityException.class, () -> WalletCipher.decrypt(new byte[10], wrong));
    }
}
