package com.carddemo.copybook.codec;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ZonedDecimalCodec {
    private ZonedDecimalCodec() {
    }

    public static long decodeUnsigned(byte[] bytes, int offset, int digitCount) {
        String digits = decodeUnsignedDigits(bytes, offset, digitCount);
        long value = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            if (value > (Long.MAX_VALUE - digit) / 10) {
                throw new CopybookCodecException(
                        "Unsigned zoned-decimal overflows long at offset " + offset
                                + " (expected at most 18 digits, actual " + digitCount + ")");
            }
            value = value * 10 + digit;
        }
        return value;
    }

    public static String decodeUnsignedDigits(byte[] bytes, int offset, int digitCount) {
        EbcdicCodec.checkRange(bytes, offset, digitCount);
        StringBuilder digits = new StringBuilder(digitCount);
        for (int i = 0; i < digitCount; i++) {
            digits.append((char) ('0' + decodeHighOrderDigit(bytes, offset + i)));
        }
        return digits.toString();
    }

    public static BigDecimal decodeSigned(byte[] bytes, int offset, int digitCount, int scale) {
        if (digitCount < 1) {
            throw new CopybookCodecException("Signed zoned-decimal digit count must be positive: " + digitCount);
        }
        EbcdicCodec.checkRange(bytes, offset, digitCount);
        StringBuilder digits = new StringBuilder(digitCount);
        for (int i = 0; i < digitCount - 1; i++) {
            digits.append((char) ('0' + decodeHighOrderDigit(bytes, offset + i)));
        }

        int low = Byte.toUnsignedInt(bytes[offset + digitCount - 1]);
        int zone = low & 0xF0;
        boolean validLow = (low >= 0xC0 && low <= 0xC9)
                || (low >= 0xD0 && low <= 0xD9)
                || (low >= 0xF0 && low <= 0xF9);
        if (!validLow) {
            throw invalidDigit(low, offset + digitCount - 1, "C0-C9, D0-D9, or F0-F9");
        }
        digits.append((char) ('0' + (low & 0x0F)));
        BigDecimal value = new BigDecimal(digits.toString()).movePointLeft(scale);
        return zone == 0xD0 ? value.negate() : value;
    }

    public static byte[] encodeUnsigned(long value, int digitCount) {
        if (value < 0) {
            throw new CopybookCodecException("Unsigned zoned-decimal cannot encode negative value: " + value);
        }
        String digits = Long.toString(value);
        if (digits.length() > digitCount) {
            throw new CopybookCodecException(
                    "Unsigned zoned-decimal value is too wide: expected " + digitCount
                            + " digits, actual " + digits.length());
        }
        return encodeDigits(digits, digitCount, false);
    }

    public static byte[] encodeSigned(BigDecimal value, int digitCount, int scale) {
        BigDecimal scaled;
        try {
            scaled = value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new CopybookCodecException(
                    "Signed zoned-decimal has more than " + scale + " fractional digits: " + value, exception);
        }
        String digits = scaled.abs().unscaledValue().toString();
        if (digits.length() > digitCount) {
            throw new CopybookCodecException(
                    "Signed zoned-decimal value is too wide: expected " + digitCount
                            + " digits, actual " + digits.length());
        }
        return encodeDigits(digits, digitCount, true, scaled.signum() < 0);
    }

    private static byte[] encodeDigits(String digits, int digitCount, boolean signed) {
        return encodeDigits(digits, digitCount, signed, false);
    }

    private static byte[] encodeDigits(String digits, int digitCount, boolean signed, boolean negative) {
        byte[] encoded = new byte[digitCount];
        int firstDigit = digitCount - digits.length();
        for (int i = 0; i < digitCount; i++) {
            int digit = i < firstDigit ? 0 : digits.charAt(i - firstDigit) - '0';
            encoded[i] = (byte) (0xF0 | digit);
        }
        if (signed) {
            int digit = encoded[digitCount - 1] & 0x0F;
            encoded[digitCount - 1] = (byte) ((negative ? 0xD0 : 0xC0) | digit);
        }
        return encoded;
    }

    private static CopybookCodecException invalidDigit(int value, int offset, String expected) {
        return new CopybookCodecException(
                String.format("Invalid zoned-decimal byte 0x%02X at offset %d (expected %s)",
                        value, offset, expected));
    }

    private static int decodeHighOrderDigit(byte[] bytes, int offset) {
        int value = Byte.toUnsignedInt(bytes[offset]);
        if (value < 0xF0 || value > 0xF9) {
            throw invalidDigit(value, offset, "F0-F9");
        }
        return value & 0x0F;
    }
}
