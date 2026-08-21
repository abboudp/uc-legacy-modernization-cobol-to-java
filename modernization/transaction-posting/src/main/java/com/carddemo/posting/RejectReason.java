package com.carddemo.posting;

/**
 * The {@code WS-VALIDATION-FAIL-REASON} codes and descriptions set by paragraph {@code 1500-VALIDATE-TRAN}.
 * Codes and text are part of the reject file contract, so they are reproduced verbatim.
 */
public enum RejectReason {
    INVALID_CARD_NUMBER(100, "INVALID CARD NUMBER FOUND"),
    ACCOUNT_NOT_FOUND(101, "ACCOUNT RECORD NOT FOUND"),
    OVERLIMIT(102, "OVERLIMIT TRANSACTION"),
    ACCOUNT_EXPIRED(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");

    private final int code;
    private final String description;

    RejectReason(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return code;
    }

    public String description() {
        return description;
    }

    /** The 80-byte {@code WS-VALIDATION-TRAILER}: a 4-digit reason code plus a 76-character description. */
    public String trailer() {
        return CobolFields.unsigned(code, 4) + CobolFields.pad(description, 76);
    }
}
