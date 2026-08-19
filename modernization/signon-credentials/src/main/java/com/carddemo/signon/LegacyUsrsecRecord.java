package com.carddemo.signon;

import java.util.ArrayList;
import java.util.List;

/**
 * One 80-byte {@code USRSEC} record as it exists today: {@code SEC-USER-DATA}
 * ({@code app/cpy/CSUSR01Y.cpy:17-23}), including the clear-text {@code SEC-USR-PWD PIC X(08)}
 * ({@code :21}) that this slice exists to retire.
 *
 * <p>Values are the canonical (trailing-blank-stripped) form of the fixed-width fields. The password
 * is kept as read, because it is the input to {@link UsrsecMigration} and nothing else.
 */
public record LegacyUsrsecRecord(
        String userId,
        String firstName,
        String lastName,
        String clearTextPassword,
        char userTypeCode) {

    /**
     * Parse a single record from 80 characters laid out per {@code app/cpy/CSUSR01Y.cpy}. The caller
     * supplies characters, not bytes: the encoding of the dataset is deliberately not this slice's
     * concern.
     */
    public static LegacyUsrsecRecord parse(String record) {
        if (record.length() != CobolField.RECORD_LENGTH) {
            throw new IllegalArgumentException(
                    "SEC-USER-DATA is " + CobolField.RECORD_LENGTH + " bytes; got " + record.length());
        }
        int offset = 0;
        String id = record.substring(offset, offset += CobolField.USER_ID_WIDTH);
        String first = record.substring(offset, offset += CobolField.FIRST_NAME_WIDTH);
        String last = record.substring(offset, offset += CobolField.LAST_NAME_WIDTH);
        String password = record.substring(offset, offset += CobolField.PASSWORD_WIDTH);
        char type = record.charAt(offset);
        return new LegacyUsrsecRecord(
                CobolField.canonical(id),
                CobolField.canonical(first),
                CobolField.canonical(last),
                CobolField.canonical(password),
                type);
    }

    /** Parse a whole unblocked {@code USRSEC} sequential file: fixed 80-character records, no delimiters. */
    public static List<LegacyUsrsecRecord> parseAll(String contents) {
        if (contents.length() % CobolField.RECORD_LENGTH != 0) {
            throw new IllegalArgumentException(
                    "USRSEC file length " + contents.length() + " is not a multiple of " + CobolField.RECORD_LENGTH);
        }
        List<LegacyUsrsecRecord> records = new ArrayList<>();
        for (int start = 0; start < contents.length(); start += CobolField.RECORD_LENGTH) {
            records.add(parse(contents.substring(start, start + CobolField.RECORD_LENGTH)));
        }
        return records;
    }
}
