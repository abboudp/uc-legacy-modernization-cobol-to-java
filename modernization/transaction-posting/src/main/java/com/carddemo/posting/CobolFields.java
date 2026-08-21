package com.carddemo.posting;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Encoding and decoding of the COBOL {@code DISPLAY} field types used by the CardDemo posting datasets:
 * unsigned {@code PIC 9(n)} and signed {@code PIC S9(n)V9(s)} with a trailing overpunched sign.
 */
public final class CobolFields {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private CobolFields() {
    }

    /** Reads {@code length} characters at {@code offset}, right-padding with spaces past the end of the record. */
    public static String text(String record, int offset, int length) {
        int from = Math.min(offset, record.length());
        int to = Math.min(offset + length, record.length());
        String slice = record.substring(from, to);
        return slice.length() == length ? slice : pad(slice, length);
    }

    /** Left-truncates or right-pads {@code value} with spaces to exactly {@code length} characters. */
    public static String pad(String value, int length) {
        if (value.length() == length) {
            return value;
        }
        if (value.length() > length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }

    /** Decodes an unsigned {@code PIC 9(n)} field, treating blanks as zero. */
    public static long unsigned(String record, int offset, int length) {
        String digits = text(record, offset, length).trim();
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    /** Encodes an unsigned {@code PIC 9(n)} field, zero-filled on the left, high-order digits truncated. */
    public static String unsigned(long value, int length) {
        String digits = Long.toString(Math.abs(value));
        return digits.length() >= length
                ? digits.substring(digits.length() - length)
                : "0".repeat(length - digits.length()) + digits;
    }

    /**
     * Decodes a signed {@code PIC S9(n)V9(scale)} field whose sign is overpunched onto the last digit,
     * which is how the CardDemo sequential datasets store balances and amounts.
     */
    public static BigDecimal signed(String record, int offset, int length, int scale) {
        String field = text(record, offset, length);
        if (field.isBlank()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        char last = field.charAt(length - 1);
        String leading = field.substring(0, length - 1);
        boolean negative;
        char lastDigit;
        int positive = POSITIVE_OVERPUNCH.indexOf(last);
        int minus = NEGATIVE_OVERPUNCH.indexOf(last);
        if (positive >= 0) {
            negative = false;
            lastDigit = (char) ('0' + positive);
        } else if (minus >= 0) {
            negative = true;
            lastDigit = (char) ('0' + minus);
        } else if (Character.isDigit(last)) {
            negative = false;
            lastDigit = last;
        } else {
            throw new PostingDataException("Unrecognised sign overpunch '" + last + "' in field \"" + field + '"');
        }
        BigDecimal magnitude = new BigDecimal(new BigInteger(leading + lastDigit), scale);
        return negative ? magnitude.negate() : magnitude;
    }

    /**
     * Encodes a signed {@code PIC S9(n)V9(scale)} field with a trailing overpunched sign.
     *
     * <p>Digits that do not fit are dropped from the high order end, reproducing the silent truncation a
     * COBOL {@code MOVE} into a too-small numeric field performs (the legacy program declares no
     * {@code ON SIZE ERROR} handler).
     */
    public static String signed(BigDecimal value, int length, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.DOWN);
        boolean negative = scaled.signum() < 0;
        String digits = scaled.abs().unscaledValue().toString();
        if (digits.length() >= length) {
            digits = digits.substring(digits.length() - length);
        } else {
            digits = "0".repeat(length - digits.length()) + digits;
        }
        int lastDigit = digits.charAt(length - 1) - '0';
        char overpunch = negative ? NEGATIVE_OVERPUNCH.charAt(lastDigit) : POSITIVE_OVERPUNCH.charAt(lastDigit);
        return digits.substring(0, length - 1) + overpunch;
    }
}
