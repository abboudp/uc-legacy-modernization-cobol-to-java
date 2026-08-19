package com.abboudp.cardxref;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Objects;

/**
 * The fixed-width 50-byte CARDXREF record from CVACT03Y.cpy.
 */
public record CardXrefRecord(String cardNum, String custId, String acctId, byte[] filler) {
    public static final int RECORD_LENGTH = 50;
    public static final int CARD_LENGTH = 16;
    public static final int CUSTOMER_LENGTH = 9;
    public static final int ACCOUNT_LENGTH = 11;
    public static final int FILLER_LENGTH = 14;
    public static final Charset EBCDIC_CHARSET = Charset.forName("IBM037");

    public CardXrefRecord {
        cardNum = requireFixedWidth(cardNum, CARD_LENGTH, "cardNum");
        custId = requireDigits(custId, CUSTOMER_LENGTH, "custId");
        acctId = requireDigits(acctId, ACCOUNT_LENGTH, "acctId");
        filler = requireFiller(filler);
    }

    @Override
    public byte[] filler() {
        return filler.clone();
    }

    /**
     * Decodes one complete CARDXREF record using IBM037 (CP037).
     *
     * @param raw exactly 50 bytes in the CVACT03Y layout
     * @return the decoded immutable record
     */
    public static CardXrefRecord decode(byte[] raw) {
        Objects.requireNonNull(raw, "raw record must not be null");
        if (raw.length != RECORD_LENGTH) {
            throw new IllegalArgumentException(
                    "CARDXREF record must be exactly 50 bytes, got " + raw.length);
        }

        String cardNum = decode(raw, 0, CARD_LENGTH, "card number");
        String custId = decodeNumeric(raw, CARD_LENGTH, CUSTOMER_LENGTH, "customer id");
        String acctId = decodeNumeric(raw, CARD_LENGTH + CUSTOMER_LENGTH, ACCOUNT_LENGTH, "account id");
        return new CardXrefRecord(
                cardNum,
                custId,
                acctId,
                Arrays.copyOfRange(raw, CARD_LENGTH + CUSTOMER_LENGTH + ACCOUNT_LENGTH, RECORD_LENGTH));
    }

    /**
     * Encodes this record into the original fixed-width layout.
     */
    public byte[] encode() {
        byte[] encoded = new byte[RECORD_LENGTH];
        copyEncoded(cardNum, encoded, 0, CARD_LENGTH, "card number");
        copyEncoded(custId, encoded, CARD_LENGTH, CUSTOMER_LENGTH, "customer id");
        copyEncoded(acctId, encoded, CARD_LENGTH + CUSTOMER_LENGTH, ACCOUNT_LENGTH, "account id");
        System.arraycopy(filler, 0, encoded, CARD_LENGTH + CUSTOMER_LENGTH + ACCOUNT_LENGTH, FILLER_LENGTH);
        return encoded;
    }

    private static String decode(byte[] raw, int offset, int length, String field) {
        return decodeBytes(raw, offset, length, field);
    }

    private static String decodeNumeric(byte[] raw, int offset, int length, String field) {
        for (int index = offset; index < offset + length; index++) {
            int value = raw[index] & 0xff;
            if (value < 0xf0 || value > 0xf9) {
                throw new IllegalArgumentException(
                        field + " contains non-EBCDIC-digit byte 0x" + String.format("%02X", value)
                                + " at offset " + index);
            }
        }
        return decodeBytes(raw, offset, length, field);
    }

    private static String decodeBytes(byte[] raw, int offset, int length, String field) {
        try {
            CharBuffer chars = EBCDIC_CHARSET.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw, offset, length));
            return chars.toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Unable to decode " + field + " as IBM037", exception);
        }
    }

    private static String requireFixedWidth(String value, int length, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.length() != length) {
            throw new IllegalArgumentException(field + " must be exactly " + length + " characters");
        }
        return value;
    }

    private static String requireDigits(String value, int length, String field) {
        requireFixedWidth(value, length, field);
        if (!value.chars().allMatch(character -> character >= '0' && character <= '9')) {
            throw new IllegalArgumentException(field + " must contain only decimal digits");
        }
        return value;
    }

    private static byte[] requireFiller(byte[] value) {
        Objects.requireNonNull(value, "filler must not be null");
        if (value.length != FILLER_LENGTH) {
            throw new IllegalArgumentException("filler must be exactly 14 bytes");
        }
        return value.clone();
    }

    private static void copyEncoded(String value, byte[] target, int offset, int length, String field) {
        byte[] bytes = encodeText(value, field);
        if (bytes.length != length) {
            throw new IllegalArgumentException(
                    field + " encodes to " + bytes.length + " bytes, expected " + length);
        }
        System.arraycopy(bytes, 0, target, offset, length);
    }

    private static byte[] encodeText(String value, String field) {
        try {
            ByteBuffer bytes = EBCDIC_CHARSET.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] encoded = new byte[bytes.remaining()];
            bytes.get(encoded);
            return encoded;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Unable to encode " + field + " as IBM037", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CardXrefRecord that)) {
            return false;
        }
        return cardNum.equals(that.cardNum)
                && custId.equals(that.custId)
                && acctId.equals(that.acctId)
                && Arrays.equals(filler, that.filler);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(cardNum, custId, acctId);
        return 31 * result + Arrays.hashCode(filler);
    }
}
