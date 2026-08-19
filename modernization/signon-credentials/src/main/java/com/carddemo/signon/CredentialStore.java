package com.carddemo.signon;

import java.util.Optional;

/**
 * Replacement for the {@code EXEC CICS READ DATASET('USRSEC')} at
 * {@code app/cbl/COSGN00C.cbl:211-219}: a keyed lookup of one user by
 * {@code SEC-USR-ID} that either finds the record or does not.
 *
 * <p>The COBOL distinguishes three responses ({@code :221-257}): {@code 0} found, {@code 13}
 * (NOTFND) not found, and anything else "Unable to verify the User". Only found/not-found is part of
 * this slice; an infrastructure failure surfaces as an exception, which is the same distinction.
 */
public interface CredentialStore {

    /**
     * Look up by user id. The id is matched on its canonical form, so the trailing blanks of the
     * 8-byte {@code SEC-USR-ID} key are not significant.
     */
    Optional<UserRecord> findByUserId(String userId);

    /** Store or replace a user record. */
    void save(UserRecord user);
}
