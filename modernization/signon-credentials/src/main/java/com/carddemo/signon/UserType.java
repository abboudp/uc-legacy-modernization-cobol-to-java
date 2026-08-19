package com.carddemo.signon;

/**
 * {@code SEC-USR-TYPE PIC X(01)} ({@code app/cpy/CSUSR01Y.cpy:22}) turned into the routing decision
 * the COBOL makes.
 *
 * <p>{@code app/cbl/COSGN00C.cbl:227} moves {@code SEC-USR-TYPE} into {@code CDEMO-USER-TYPE} and
 * {@code :230} tests the condition name {@code CDEMO-USRTYP-ADMIN}, which is {@code VALUE 'A'}
 * ({@code app/cpy/COCOM01Y.cpy:27}). The test is a two-way {@code IF}/{@code ELSE}: {@code 'A'}
 * transfers to {@code COADM01C} ({@code :231-234}) and <em>everything else</em> transfers to
 * {@code COMEN01C} ({@code :236-239}). The copybook also declares {@code CDEMO-USRTYP-USER VALUE 'U'}
 * ({@code app/cpy/COCOM01Y.cpy:28}), but sign-on never tests it, so an unknown type code is routed to
 * the regular menu rather than rejected.
 */
public enum UserType {

    /** {@code SEC-USR-TYPE = 'A'} — admin menu. */
    ADMIN('A', "COADM01C"),

    /** Any other code, including the declared {@code 'U'} — regular menu. */
    USER('U', "COMEN01C");

    private final char canonicalCode;
    private final String menuProgram;

    UserType(char canonicalCode, String menuProgram) {
        this.canonicalCode = canonicalCode;
        this.menuProgram = menuProgram;
    }

    /** The code this type writes back into {@code SEC-USR-TYPE}/{@code CDEMO-USER-TYPE}. */
    public char canonicalCode() {
        return canonicalCode;
    }

    /** The {@code EXEC CICS XCTL PROGRAM} target sign-on hands control to. */
    public String menuProgram() {
        return menuProgram;
    }

    /** {@code 'A'} is admin; every other code falls into the {@code ELSE} branch. */
    public static UserType fromCode(char code) {
        return code == 'A' ? ADMIN : USER;
    }
}
