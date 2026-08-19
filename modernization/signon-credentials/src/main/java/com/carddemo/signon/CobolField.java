package com.carddemo.signon;

/**
 * Fixed-width field semantics carried over from the COBOL record layouts.
 *
 * <p>The widths come from {@code app/cpy/CSUSR01Y.cpy:18-23}. They are not cosmetic: the user id is
 * {@code SEC-USR-ID PIC X(08)}, it is the VSAM key used at {@code app/cbl/COSGN00C.cbl:215-216},
 * and the same width is embedded in the communication area at {@code app/cpy/COCOM01Y.cpy:25} and in
 * every screen, so it outlives the mainframe.
 *
 * <p>Because the fields are fixed-width and blank-padded, {@code "user1   "} and {@code "user1"} are
 * the same value; a value is canonicalised by stripping trailing blanks, and enforced by
 * {@link #require(String, int, String)} at the point a record is built.
 */
public final class CobolField {

    /** {@code SEC-USR-ID PIC X(08)} — {@code app/cpy/CSUSR01Y.cpy:18}. */
    public static final int USER_ID_WIDTH = 8;
    /** {@code SEC-USR-FNAME PIC X(20)} — {@code app/cpy/CSUSR01Y.cpy:19}. */
    public static final int FIRST_NAME_WIDTH = 20;
    /** {@code SEC-USR-LNAME PIC X(20)} — {@code app/cpy/CSUSR01Y.cpy:20}. */
    public static final int LAST_NAME_WIDTH = 20;
    /** {@code SEC-USR-PWD PIC X(08)} — {@code app/cpy/CSUSR01Y.cpy:21}. */
    public static final int PASSWORD_WIDTH = 8;
    /** {@code SEC-USR-TYPE PIC X(01)} — {@code app/cpy/CSUSR01Y.cpy:22}. */
    public static final int USER_TYPE_WIDTH = 1;
    /** {@code SEC-USR-FILLER PIC X(23)} — {@code app/cpy/CSUSR01Y.cpy:23}. */
    public static final int FILLER_WIDTH = 23;

    /** Whole {@code SEC-USER-DATA} record: 8 + 20 + 20 + 8 + 1 + 23 = 80 bytes. */
    public static final int RECORD_LENGTH =
            USER_ID_WIDTH + FIRST_NAME_WIDTH + LAST_NAME_WIDTH + PASSWORD_WIDTH + USER_TYPE_WIDTH + FILLER_WIDTH;

    private CobolField() {
    }

    /**
     * Canonical form of a value held in a blank-padded fixed-width field: trailing blanks removed.
     * Leading blanks are significant in COBOL and are kept.
     */
    public static String canonical(String value) {
        if (value == null) {
            return null;
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    /** Canonicalise and reject anything that would not fit the field. This is what enforces the widths. */
    public static String require(String value, int width, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        String canonical = canonical(value);
        if (canonical.length() > width) {
            throw new IllegalArgumentException(
                    fieldName + " is " + canonical.length() + " characters; the COBOL field is PIC X(" + width + ")");
        }
        return canonical;
    }

    /**
     * The effect of a COBOL {@code MOVE} of an alphanumeric item into a {@code PIC X(width)} item:
     * left-justified, truncated on the right, blank-padded. Used for credentials arriving from a
     * caller, mirroring the moves at {@code app/cbl/COSGN00C.cbl:132-136}, where the receiving items
     * are {@code WS-USER-ID}/{@code WS-USER-PWD PIC X(08)} ({@code :45-46}).
     */
    public static String moveInto(String value, int width) {
        if (value == null) {
            return " ".repeat(width);
        }
        String truncated = value.length() > width ? value.substring(0, width) : value;
        return truncated + " ".repeat(width - truncated.length());
    }
}
