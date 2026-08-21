package com.carddemo.posting;

/** The {@code FD-TRAN-CAT-KEY} of the TCATBALF KSDS: account id, transaction type and category. */
public record TransactionCategoryKey(long accountId, String typeCode, int categoryCode) {

    /** The 17-character key image the legacy program displays when a row has to be created. */
    public String image() {
        return CobolFields.unsigned(accountId, 11)
                + CobolFields.pad(typeCode, 2)
                + CobolFields.unsigned(categoryCode, 4);
    }
}
