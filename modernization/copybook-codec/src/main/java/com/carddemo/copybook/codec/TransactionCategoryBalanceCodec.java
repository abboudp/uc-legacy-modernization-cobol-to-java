package com.carddemo.copybook.codec;

public final class TransactionCategoryBalanceCodec {
    public static final int RECORD_LENGTH = 50;

    private TransactionCategoryBalanceCodec() {
    }

    public static TransactionCategoryBalanceRecord decode(byte[] record, int offset) {
        requireRecord(record, offset);
        return new TransactionCategoryBalanceRecord(
                ZonedDecimalCodec.decodeUnsigned(record, offset, 11),
                DisplayFieldCodec.decode(record, offset + 11, 2),
                (int) ZonedDecimalCodec.decodeUnsigned(record, offset + 13, 4),
                ZonedDecimalCodec.decodeSigned(record, offset + 17, 11, 2),
                DisplayFieldCodec.decode(record, offset + 28, 22));
    }

    public static byte[] encode(TransactionCategoryBalanceRecord record) {
        byte[] encoded = new byte[RECORD_LENGTH];
        copy(encoded, 0, ZonedDecimalCodec.encodeUnsigned(record.accountId(), 11));
        copy(encoded, 11, DisplayFieldCodec.encode(record.transactionTypeCode(), 2));
        copy(encoded, 13, ZonedDecimalCodec.encodeUnsigned(record.categoryCode(), 4));
        copy(encoded, 17, ZonedDecimalCodec.encodeSigned(record.balance(), 11, 2));
        copy(encoded, 28, DisplayFieldCodec.encode(record.filler(), 22));
        return encoded;
    }

    private static void requireRecord(byte[] record, int offset) {
        if (offset < 0 || offset > record.length - RECORD_LENGTH) {
            throw new CopybookCodecException(
                    "Transaction category balance record is outside image: expected " + RECORD_LENGTH
                            + " bytes at offset " + offset + ", actual " + record.length);
        }
    }

    private static void copy(byte[] target, int offset, byte[] field) {
        System.arraycopy(field, 0, target, offset, field.length);
    }
}
