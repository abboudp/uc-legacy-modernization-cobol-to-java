package com.carddemo.copybook.codec;

public final class TransactionTypeCodec {
    public static final int RECORD_LENGTH = 60;

    private TransactionTypeCodec() {
    }

    public static TransactionTypeRecord decode(byte[] record, int offset) {
        requireRecord(record, offset);
        return new TransactionTypeRecord(
                DisplayFieldCodec.decode(record, offset, 2),
                DisplayFieldCodec.decode(record, offset + 2, 50),
                DisplayFieldCodec.decode(record, offset + 52, 8));
    }

    public static byte[] encode(TransactionTypeRecord record) {
        byte[] encoded = new byte[RECORD_LENGTH];
        copy(encoded, 0, DisplayFieldCodec.encode(record.transactionType(), 2));
        copy(encoded, 2, DisplayFieldCodec.encode(record.transactionTypeDescription(), 50));
        copy(encoded, 52, DisplayFieldCodec.encode(record.filler(), 8));
        return encoded;
    }

    private static void requireRecord(byte[] record, int offset) {
        if (offset < 0 || offset > record.length - RECORD_LENGTH) {
            throw new CopybookCodecException(
                    "Transaction type record is outside image: expected " + RECORD_LENGTH
                            + " bytes at offset " + offset + ", actual " + record.length);
        }
    }

    private static void copy(byte[] target, int offset, byte[] field) {
        System.arraycopy(field, 0, target, offset, field.length);
    }
}
