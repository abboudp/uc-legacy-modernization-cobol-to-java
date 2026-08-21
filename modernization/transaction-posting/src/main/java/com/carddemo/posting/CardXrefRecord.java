package com.carddemo.posting;

/** One {@code CARD-XREF-RECORD} (copybook {@code CVACT03Y}, 50 bytes). */
public record CardXrefRecord(String cardNumber, long customerId, long accountId) {

    public static final int LENGTH = 50;

    public static CardXrefRecord decode(String record) {
        String image = CobolFields.pad(record, LENGTH);
        return new CardXrefRecord(
                CobolFields.text(image, 0, 16),
                CobolFields.unsigned(image, 16, 9),
                CobolFields.unsigned(image, 25, 11));
    }

    public String encode() {
        return CobolFields.pad(cardNumber, 16)
                + CobolFields.unsigned(customerId, 9)
                + CobolFields.unsigned(accountId, 11)
                + " ".repeat(14);
    }
}
