package com.carddemo.posting;

import java.math.BigDecimal;

/** One {@code TRAN-RECORD} (copybook {@code CVTRA05Y}, 350 bytes) written to the transaction master. */
public record PostedTransaction(
        String id,
        String typeCode,
        int categoryCode,
        String source,
        String description,
        BigDecimal amount,
        long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNumber,
        String originTimestamp,
        String processedTimestamp) {

    public static final int LENGTH = 350;

    /** The field-by-field {@code MOVE} sequence of paragraph {@code 2000-POST-TRANSACTION}. */
    public static PostedTransaction from(DailyTransaction daily, String processedTimestamp) {
        return new PostedTransaction(
                daily.id(),
                daily.typeCode(),
                daily.categoryCode(),
                daily.source(),
                daily.description(),
                daily.amount(),
                daily.merchantId(),
                daily.merchantName(),
                daily.merchantCity(),
                daily.merchantZip(),
                daily.cardNumber(),
                daily.originTimestamp(),
                processedTimestamp);
    }

    public String encode() {
        return CobolFields.pad(id, 16)
                + CobolFields.pad(typeCode, 2)
                + CobolFields.unsigned(categoryCode, 4)
                + CobolFields.pad(source, 10)
                + CobolFields.pad(description, 100)
                + CobolFields.signed(amount, 11, 2)
                + CobolFields.unsigned(merchantId, 9)
                + CobolFields.pad(merchantName, 50)
                + CobolFields.pad(merchantCity, 50)
                + CobolFields.pad(merchantZip, 10)
                + CobolFields.pad(cardNumber, 16)
                + CobolFields.pad(originTimestamp, 26)
                + CobolFields.pad(processedTimestamp, 26)
                + " ".repeat(20);
    }
}
