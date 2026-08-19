package com.carddemo.signon;

/**
 * The credential-verification decision of {@code app/cbl/COSGN00C.cbl}, reimplemented against hashed
 * credentials.
 *
 * <p>What is preserved: the three-way outcome ({@code :221-257}), the fixed-width blank-padded field
 * semantics of {@code SEC-USR-ID}/{@code SEC-USR-PWD}, the upper-casing of both submitted values
 * ({@code :132-136}) and the admin/regular routing keyed off {@code SEC-USR-TYPE} ({@code :227-240}).
 *
 * <p>What is replaced: the clear-text equality test {@code IF SEC-USR-PWD = WS-USER-PWD} ({@code :223})
 * becomes a constant-time comparison of an adaptively derived key.
 */
public final class CredentialVerificationService {

    private final CredentialStore store;
    private final PasswordHasher hasher;

    public CredentialVerificationService(CredentialStore store, PasswordHasher hasher) {
        this.store = store;
        this.hasher = hasher;
    }

    /**
     * Verify a submitted id and password.
     *
     * <p>Both values are canonicalised exactly as the COBOL does before the compare: upper-cased by
     * {@code FUNCTION UPPER-CASE} and moved into {@code PIC X(08)} items ({@code app/cbl/COSGN00C.cbl:132-136},
     * items declared at {@code :45-46}), which left-justifies, truncates past 8 characters and pads with
     * blanks. Trailing blanks are therefore not significant on either field.
     */
    public VerificationResult verify(String submittedUserId, String submittedPassword) {
        String userId = canonicalizeSubmitted(submittedUserId, CobolField.USER_ID_WIDTH);
        String password = canonicalizeSubmitted(submittedPassword, CobolField.PASSWORD_WIDTH);

        // RESP 13 from the USRSEC read — app/cbl/COSGN00C.cbl:247-251.
        UserRecord user = store.findByUserId(userId).orElse(null);
        if (user == null) {
            return VerificationResult.userNotFound(userId);
        }

        if (!hasher.matches(password, user.passwordHash())) {
            return VerificationResult.wrongPassword(userId);
        }

        return VerificationResult.success(user.userId(), user.userType());
    }

    /**
     * Upper-case, truncate to the field width, then drop the padding again. The intermediate padding
     * matters: it is what makes {@code "user1"} and {@code "user1   "} the same submitted value, and
     * getting it wrong locks every user out at cutover.
     */
    private static String canonicalizeSubmitted(String value, int width) {
        String upper = value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
        return CobolField.canonical(CobolField.moveInto(upper, width));
    }
}
