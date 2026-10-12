package com.example.legacybtc;

/**
 * I convert public keys and serialized transactions between byte arrays and hexadecimal
 * Strings for recovery packages. Each byte occupies two hex digits. Decoding validates even
 * length and hexadecimal characters before parsing pairs; malformed input becomes a validation
 * error rather than partially decoded data.
 */
public final class Hex {
    private Hex() { }
    /**
     * I loop through bytes, format each unsigned value as two hexadecimal digits using
     * Locale.ROOT, and return the concatenated public String. Recovery packages use it for
     * keys and serialized transactions; encoding does not encrypt data.
     * @param bytes byte[] supplied by the calling workflow as described above
     * @return public hexadecimal text or a JSONObject representation as described above
     */
    public static String encode(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    /**
     * I reject null, odd-length, or non-hexadecimal text, allocate one byte per character
     * pair, and loop to parse each pair in base sixteen. The resulting byte array is passed to
     * public-key or transaction parsing. Malformed text raises an input error.
     * @param text String supplied by the calling workflow as described above
     * @return validated decoded bytes or an InheritancePlan as described above
     */
    public static byte[] decode(String text) {
        if (text == null || text.length() % 2 != 0 || !text.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("Invalid hexadecimal data.");
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
