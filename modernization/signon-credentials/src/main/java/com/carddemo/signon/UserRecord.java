package com.carddemo.signon;

/**
 * The target-model user, modelled field for field on {@code SEC-USER-DATA}
 * ({@code app/cpy/CSUSR01Y.cpy:17-23}) with one substitution: the clear-text
 * {@code SEC-USR-PWD PIC X(08)} becomes a {@link PasswordHash}.
 *
 * <p>The mainframe widths are kept and enforced here, by {@link CobolField#require}: the user id is
 * the {@code USRSEC} key ({@code app/cbl/COSGN00C.cbl:215-216}) and is carried in the communication
 * area at {@code app/cpy/COCOM01Y.cpy:25}, so an id longer than 8 characters cannot be represented by
 * anything downstream. The id is stored canonically, without trailing blanks.
 */
public record UserRecord(String userId, String firstName, String lastName, UserType userType, PasswordHash passwordHash) {

    public UserRecord {
        userId = CobolField.require(userId, CobolField.USER_ID_WIDTH, "SEC-USR-ID");
        firstName = CobolField.require(firstName, CobolField.FIRST_NAME_WIDTH, "SEC-USR-FNAME");
        lastName = CobolField.require(lastName, CobolField.LAST_NAME_WIDTH, "SEC-USR-LNAME");
        if (userId.isEmpty()) {
            throw new IllegalArgumentException("SEC-USR-ID must not be blank; it is the USRSEC key");
        }
        if (userType == null) {
            throw new IllegalArgumentException("SEC-USR-TYPE must not be null");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("passwordHash must not be null");
        }
    }
}
