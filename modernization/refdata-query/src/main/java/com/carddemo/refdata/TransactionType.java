package com.carddemo.refdata;

/**
 * One row of {@code CARDDEMO.TRANSACTION_TYPE}.
 *
 * <p>{@code trType} is {@code CHAR(2)} and is kept as the two characters stored on the mainframe,
 * never widened or parsed to a number: the COBOL host variable is {@code PIC X(2)}
 * (app/app-transaction-type-db2/dcl/DCLTRTYP.dcl:38) and only the screen filter redefines it as
 * {@code PIC 9(02)} (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:312).
 */
public record TransactionType(String trType, String trDescription) {

    public TransactionType {
        if (trType == null || trDescription == null) {
            throw new IllegalArgumentException("TR_TYPE and TR_DESCRIPTION are NOT NULL columns");
        }
    }
}
