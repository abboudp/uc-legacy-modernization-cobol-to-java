package com.carddemo.posting;

import java.math.BigDecimal;

/** One {@code TRAN-CAT-BAL-RECORD} (copybook {@code CVTRA01Y}, 50 bytes). */
public record TransactionCategoryBalance(TransactionCategoryKey key, BigDecimal balance, String filler) {

    public static final int LENGTH = 50;

    public static TransactionCategoryBalance decode(String record) {
        String image = CobolFields.pad(record, LENGTH);
        return new TransactionCategoryBalance(
                new TransactionCategoryKey(
                        CobolFields.unsigned(image, 0, 11),
                        CobolFields.text(image, 11, 2),
                        (int) CobolFields.unsigned(image, 13, 4)),
                CobolFields.signed(image, 17, 11, 2),
                CobolFields.text(image, 28, 22));
    }

    /**
     * Paragraph {@code 2700-A-CREATE-TCATBAL-REC}: {@code INITIALIZE} clears the record to zeros and spaces
     * before the key is moved in, so a newly created row starts from a zero balance.
     */
    public static TransactionCategoryBalance created(TransactionCategoryKey key) {
        return new TransactionCategoryBalance(key, BigDecimal.ZERO.setScale(2), " ".repeat(22));
    }

    public String encode() {
        return CobolFields.unsigned(key.accountId(), 11)
                + CobolFields.pad(key.typeCode(), 2)
                + CobolFields.unsigned(key.categoryCode(), 4)
                + CobolFields.signed(balance, 11, 2)
                + CobolFields.pad(filler, 22);
    }

    public TransactionCategoryBalance add(BigDecimal amount) {
        return new TransactionCategoryBalance(key, balance.add(amount), filler);
    }
}
