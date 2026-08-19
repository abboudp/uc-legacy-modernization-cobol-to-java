package com.carddemo.refdata;

/**
 * One row of {@code CARDDEMO.TRANSACTION_TYPE_CATEGORY}, whose primary key is the pair
 * ({@code TRC_TYPE_CODE}, {@code TRC_TYPE_CATEGORY})
 * (app/app-transaction-type-db2/ctl/DB2CREAT.ctl:79).
 *
 * <p>{@code trcTypeCategory} stays a 4-character string. The column is {@code CHAR(4)} and the
 * DCLGEN host variable is {@code PIC X(4)} (app/app-transaction-type-db2/dcl/DCLTRCAT.dcl:42),
 * but the VSAM replica layout declares the same field numeric, {@code PIC 9(04)}
 * (app/cpy/CVTRA04Y.cpy:7). Treating it as a number would drop the leading zeros that the
 * character key sorts on.
 */
public record TransactionTypeCategory(String trcTypeCode, String trcTypeCategory, String trcCatData) {

    public TransactionTypeCategory {
        if (trcTypeCode == null || trcTypeCategory == null || trcCatData == null) {
            throw new IllegalArgumentException("all three columns are NOT NULL");
        }
    }
}
