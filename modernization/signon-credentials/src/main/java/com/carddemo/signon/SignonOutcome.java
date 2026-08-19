package com.carddemo.signon;

/**
 * The three-way outcome of {@code READ-USER-SEC-FILE} in {@code app/cbl/COSGN00C.cbl:209-257}.
 *
 * <p>The COBOL keeps user-not-found and wrong-password apart and, notably, tells the terminal user
 * which it was: {@code 'User not found. Try again ...'} at {@code :249} (response 13, NOTFND) and
 * {@code 'Wrong Password. Try again ...'} at {@code :242} (record read, compare failed). The
 * distinction is preserved here because it is legacy behaviour; whether it is re-exposed to an end
 * user is a caller-side decision, discussed in the README.
 */
public enum SignonOutcome {

    /** Record read and password matched — {@code app/cbl/COSGN00C.cbl:222-240}. */
    SUCCESS("", true),

    /** RESP 13 from the {@code USRSEC} read — {@code app/cbl/COSGN00C.cbl:247-251}. */
    USER_NOT_FOUND("User not found. Try again ...", false),

    /** Record read but the password compare failed — {@code app/cbl/COSGN00C.cbl:241-246}. */
    WRONG_PASSWORD("Wrong Password. Try again ...", false);

    private final String legacyMessage;
    private final boolean authenticated;

    SignonOutcome(String legacyMessage, boolean authenticated) {
        this.legacyMessage = legacyMessage;
        this.authenticated = authenticated;
    }

    /** The message text the COBOL puts in {@code WS-MESSAGE}, verbatim. */
    public String legacyMessage() {
        return legacyMessage;
    }

    public boolean authenticated() {
        return authenticated;
    }
}
