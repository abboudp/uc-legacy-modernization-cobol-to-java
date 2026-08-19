package com.carddemo.signon;

import java.util.Optional;

/**
 * The result of a credential verification, including the routing decision.
 *
 * <p>In the COBOL the routing is not a value, it is control flow: on success
 * {@code app/cbl/COSGN00C.cbl:227} moves {@code SEC-USR-TYPE} into the communication area and
 * {@code :230-240} transfers control with {@code EXEC CICS XCTL} to {@code COADM01C} or
 * {@code COMEN01C}. Downstream code depends on that decision, so here it is a first-class part of the
 * result rather than a side effect.
 */
public record VerificationResult(SignonOutcome outcome, String userId, UserType userType) {

    public static VerificationResult success(String userId, UserType userType) {
        return new VerificationResult(SignonOutcome.SUCCESS, userId, userType);
    }

    public static VerificationResult userNotFound(String userId) {
        return new VerificationResult(SignonOutcome.USER_NOT_FOUND, userId, null);
    }

    public static VerificationResult wrongPassword(String userId) {
        return new VerificationResult(SignonOutcome.WRONG_PASSWORD, userId, null);
    }

    public boolean authenticated() {
        return outcome.authenticated();
    }

    /** {@code SEC-USR-TYPE}, present only on success — the COBOL only reaches {@code :227} when it succeeds. */
    public Optional<UserType> resolvedUserType() {
        return Optional.ofNullable(userType);
    }

    /**
     * The program sign-on would {@code XCTL} to: {@code COADM01C} ({@code app/cbl/COSGN00C.cbl:232})
     * for {@code SEC-USR-TYPE = 'A'}, otherwise {@code COMEN01C} ({@code :237}). Empty when
     * verification failed, because the COBOL re-displays the sign-on screen instead of transferring.
     */
    public Optional<String> nextProgram() {
        return resolvedUserType().map(UserType::menuProgram);
    }
}
