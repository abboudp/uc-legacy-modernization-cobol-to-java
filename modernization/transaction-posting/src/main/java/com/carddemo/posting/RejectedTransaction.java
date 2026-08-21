package com.carddemo.posting;

/**
 * One 430-byte {@code REJECT-RECORD}: the 350-byte daily transaction image followed by the 80-byte
 * validation trailer, matching {@code LRECL=430} on the {@code DALYREJS} DD of {@code app/jcl/POSTTRAN.jcl}.
 */
public record RejectedTransaction(DailyTransaction transaction, RejectReason reason) {

    public static final int LENGTH = 430;

    public String encode() {
        return CobolFields.pad(transaction.rawRecord(), DailyTransaction.LENGTH) + reason.trailer();
    }
}
