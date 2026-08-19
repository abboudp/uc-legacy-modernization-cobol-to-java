package com.carddemo.copybook.codec;

import java.nio.charset.Charset;
import java.util.Arrays;

public final class EbcdicCodec {
    public static final Charset CHARSET = Charset.forName("IBM037");
    public static final byte SPACE = (byte) 0x40;

    private EbcdicCodec() {
    }

    public static String decodeDisplay(byte[] bytes, int offset, int length) {
        checkRange(bytes, offset, length);
        int end = offset + length;
        while (end > offset && bytes[end - 1] == SPACE) {
            end--;
        }
        return new String(bytes, offset, end - offset, CHARSET);
    }

    public static byte[] encodeDisplay(String value, int length) {
        byte[] encoded = value.getBytes(CHARSET);
        if (encoded.length > length) {
            throw new CopybookCodecException(
                    "Display value is too long: expected at most " + length + " bytes, actual " + encoded.length);
        }
        byte[] field = new byte[length];
        Arrays.fill(field, SPACE);
        System.arraycopy(encoded, 0, field, 0, encoded.length);
        return field;
    }

    static void checkRange(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new CopybookCodecException(
                    "Field is outside record: offset " + offset + ", length " + length
                            + ", actual " + bytes.length);
        }
    }
}
