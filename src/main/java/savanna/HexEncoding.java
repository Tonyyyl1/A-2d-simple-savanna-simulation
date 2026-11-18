package savanna;

/** Allocation-bounded lowercase hexadecimal encoding for digest identities. */
public final class HexEncoding
{
    private static final char[] LOWERCASE = "0123456789abcdef".toCharArray();

    private HexEncoding()
    {
    }

    public static String lowercase(byte[] bytes)
    {
        if(bytes == null) {
            throw new IllegalArgumentException("bytes are required");
        }
        char[] encoded = new char[bytes.length * 2];
        for(int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            encoded[index * 2] = LOWERCASE[value >>> 4];
            encoded[index * 2 + 1] = LOWERCASE[value & 0x0f];
        }
        return new String(encoded);
    }
}
