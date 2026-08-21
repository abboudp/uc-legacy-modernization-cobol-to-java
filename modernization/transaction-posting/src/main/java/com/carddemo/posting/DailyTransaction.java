package com.carddemo.posting;

import java.math.BigDecimal;

/**
 * One {@code DALYTRAN-RECORD} (copybook {@code CVTRA06Y}, 350 bytes).
 *
 * <p>{@link #rawRecord()} keeps the undecoded image because the legacy reject path moves the record bytes
 * straight into {@code REJECT-TRAN-DATA}; rebuilding it from the decoded fields would not be byte-exact for
 * records whose numeric fields are stored in a non-canonical form.
 */
public record DailyTransaction(
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
        String processedTimestamp,
        String rawRecord) {

    public static final int LENGTH = 350;

    public static DailyTransaction decode(String record) {
        if (record.length() > LENGTH) {
            throw new PostingDataException("DALYTRAN record longer than " + LENGTH + " bytes: " + record.length());
        }
        String image = CobolFields.pad(record, LENGTH);
        return new DailyTransaction(
                CobolFields.text(image, 0, 16),
                CobolFields.text(image, 16, 2),
                (int) CobolFields.unsigned(image, 18, 4),
                CobolFields.text(image, 22, 10),
                CobolFields.text(image, 32, 100),
                CobolFields.signed(image, 132, 11, 2),
                CobolFields.unsigned(image, 143, 9),
                CobolFields.text(image, 152, 50),
                CobolFields.text(image, 202, 50),
                CobolFields.text(image, 252, 10),
                CobolFields.text(image, 262, 16),
                CobolFields.text(image, 278, 26),
                CobolFields.text(image, 304, 26),
                image);
    }

    /** The {@code DALYTRAN-ORIG-TS (1:10)} reference compared against the account expiration date. */
    public String originDate() {
        return originTimestamp.substring(0, 10);
    }
}
