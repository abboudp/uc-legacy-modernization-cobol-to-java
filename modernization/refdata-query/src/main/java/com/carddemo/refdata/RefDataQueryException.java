package com.carddemo.refdata;

/**
 * Unchecked wrapper for {@link java.sql.SQLException}.
 *
 * <p>The COBOL routes every non-zero, non-{@code +100} SQLCODE through {@code DSNTIAC} and formats
 * it into a screen message ({@code 9999-FORMAT-DB2-MESSAGE},
 * app/app-transaction-type-db2/cbl/COTRTLIC.cbl:1955-1962). Only the message-carrying end-of-data
 * cases are modelled as data here; genuine failures are thrown, because there is no screen to put
 * them on in this slice.
 */
public class RefDataQueryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RefDataQueryException(String action, Throwable cause) {
        super(action + " failed: " + cause.getMessage(), cause);
    }
}
