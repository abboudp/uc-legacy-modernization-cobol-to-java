package com.carddemo.interestrate;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Codec for the six-byte EBCDIC representation of S9(04)V99.
 */
public final class SignedZonedDecimalCodec {
    private SignedZonedDecimalCodec() {
    }

    public static BigDecimal decode(byte[] bytes, int offset) {
        if (bytes == null || offset < 0 || offset + 6 > (bytes == null ? 0 : bytes.length)) {
            throw new IllegalArgumentException("S9(04)V99 requires six bytes at offset " + offset);
        }
        int magnitude = 0;
        for (int index = 0; index < 5; index++) {
            int digit = unsignedDigit(bytes[offset + index], offset + index);
            magnitude = magnitude * 10 + digit;
        }

        int last = Byte.toUnsignedInt(bytes[offset + 5]);
        boolean negative;
        if (last >= 0xC0 && last <= 0xC9) {
            negative = false;
        } else if (last >= 0xD0 && last <= 0xD9) {
            negative = true;
        } else if (last >= 0xF0 && last <= 0xF9) {
            negative = false;
        } else {
            throw invalidByte(last, offset + 5);
        }
        BigDecimal value = BigDecimal.valueOf(magnitude, 2);
        return negative ? value.negate() : value;
    }

    public static byte[] encode(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("S9(04)V99 value must not be null");
        }
        BigDecimal scaled;
        try {
            scaled = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("S9(04)V99 value must have scale no greater than 2: " + value, exception);
        }
        if (scaled.abs().compareTo(BigDecimal.valueOf(999.99)) > 0) {
            throw new IllegalArgumentException("Signed-zoned rate exceeds five encodable magnitude digits: " + value);
        }
        int magnitude = scaled.movePointRight(2).abs().intValueExact();
        byte[] encoded = new byte[6];
        int trailingDigit = magnitude % 10;
        for (int index = 4; index >= 0; index--) {
            encoded[index] = (byte) (0xF0 + magnitude % 10);
            magnitude /= 10;
        }
        int signBase = scaled.signum() < 0 ? 0xD0 : 0xC0;
        encoded[5] = (byte) (signBase + trailingDigit);
        return encoded;
    }

    private static int unsignedDigit(byte value, int offset) {
        int unsigned = Byte.toUnsignedInt(value);
        if (unsigned < 0xF0 || unsigned > 0xF9) {
            throw invalidByte(unsigned, offset);
        }
        return unsigned - 0xF0;
    }

    private static IllegalArgumentException invalidByte(int value, int offset) {
        return new IllegalArgumentException(String.format(
                "Invalid S9(04)V99 byte 0x%02X at offset %d", value, offset));
    }
}
