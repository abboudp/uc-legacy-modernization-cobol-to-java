package com.carddemo.cardlist;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

public final class CardRecordCodec {
    public static final int RECORD_LENGTH = 150;
    private static final Charset EBCDIC = Charset.forName("IBM037");

    public CardRecord decode(byte[] bytes) {
        requireRecordLength(bytes);
        return new CardRecord(
                text(bytes, 0, 16),
                text(bytes, 16, 11),
                text(bytes, 27, 3),
                text(bytes, 30, 50),
                text(bytes, 80, 10),
                text(bytes, 90, 1),
                Arrays.copyOfRange(bytes, 91, RECORD_LENGTH));
    }

    public byte[] encode(CardRecord record) {
        byte[] result = new byte[RECORD_LENGTH];
        put(result, 0, 16, record.cardNumber());
        put(result, 16, 11, record.cardAcctId());
        put(result, 27, 3, record.cardCvvCd());
        put(result, 30, 50, record.embossedName());
        put(result, 80, 10, record.cardExpirationDate());
        put(result, 90, 1, record.cardActiveStatus());
        byte[] filler = record.filler();
        System.arraycopy(filler, 0, result, 91, filler.length);
        return result;
    }

    private static String text(byte[] bytes, int offset, int length) {
        return EBCDIC.decode(ByteBuffer.wrap(bytes, offset, length)).toString();
    }

    private static void put(byte[] destination, int offset, int width, String value) {
        byte[] encoded = value.getBytes(EBCDIC);
        if (encoded.length != width) {
            throw new IllegalArgumentException("field at offset " + offset
                    + " must encode to exactly " + width + " bytes");
        }
        System.arraycopy(encoded, 0, destination, offset, width);
    }

    private static void requireRecordLength(byte[] bytes) {
        if (bytes == null || bytes.length != RECORD_LENGTH) {
            throw new IllegalArgumentException("CARD-RECORD must be exactly 150 bytes");
        }
    }
}
