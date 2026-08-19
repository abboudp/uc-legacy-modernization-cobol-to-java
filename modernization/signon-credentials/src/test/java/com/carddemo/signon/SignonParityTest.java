package com.carddemo.signon;

import java.security.SecureRandom;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity of the sign-on decision in {@code app/cbl/COSGN00C.cbl:209-257}, seeded from the
 * repository's own {@code USRSEC} data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignonParityTest {

    /** Lower than {@link PasswordHasher#DEFAULT_ITERATIONS} to keep the suite quick; same code path. */
    private static final int TEST_ITERATIONS = 10_000;

    private InMemoryCredentialStore store;
    private CredentialVerificationService service;
    private List<LegacyUsrsecRecord> legacyRecords;

    @BeforeEach
    void migrateFixture() {
        PasswordHasher hasher = new PasswordHasher(new SecureRandom(), TEST_ITERATIONS);
        store = new InMemoryCredentialStore();
        legacyRecords = UsrsecFixture.records();
        int migrated = new UsrsecMigration(hasher).migrateInto(UsrsecFixture.contents(), store);
        assertEquals(legacyRecords.size(), migrated);
        service = new CredentialVerificationService(store, hasher);
    }

    @Test
    void everySeededUserAuthenticatesWithItsLegacyPassword() {
        for (LegacyUsrsecRecord legacy : legacyRecords) {
            VerificationResult result = service.verify(legacy.userId(), legacy.clearTextPassword());
            assertEquals(SignonOutcome.SUCCESS, result.outcome(), legacy.userId());
            assertTrue(result.authenticated());
        }
    }

    @Test
    void wrongPasswordIsRejected() {
        VerificationResult result = service.verify("ADMIN001", "NOTRIGHT");
        assertEquals(SignonOutcome.WRONG_PASSWORD, result.outcome());
        assertFalse(result.authenticated());
        assertTrue(result.nextProgram().isEmpty(), "no XCTL happens on a failed compare");
    }

    @Test
    void unknownUserIsDistinguishableFromWrongPassword() {
        // app/cbl/COSGN00C.cbl:247-251 (RESP 13) versus :241-246.
        VerificationResult unknown = service.verify("NOSUCHUS", "PASSWORD");
        VerificationResult wrongPassword = service.verify("ADMIN001", "NOTRIGHT");

        assertEquals(SignonOutcome.USER_NOT_FOUND, unknown.outcome());
        assertEquals(SignonOutcome.WRONG_PASSWORD, wrongPassword.outcome());
        assertNotEquals(unknown.outcome(), wrongPassword.outcome());
        assertEquals("User not found. Try again ...", unknown.outcome().legacyMessage());
        assertEquals("Wrong Password. Try again ...", wrongPassword.outcome().legacyMessage());
    }

    @Test
    void routingMatchesSecUsrTypeForEverySeededUser() {
        for (LegacyUsrsecRecord legacy : legacyRecords) {
            VerificationResult result = service.verify(legacy.userId(), legacy.clearTextPassword());

            UserType expected = legacy.userTypeCode() == 'A' ? UserType.ADMIN : UserType.USER;
            assertEquals(expected, result.resolvedUserType().orElseThrow(), legacy.userId());
            // app/cbl/COSGN00C.cbl:231-234 versus :236-239.
            assertEquals(expected == UserType.ADMIN ? "COADM01C" : "COMEN01C",
                    result.nextProgram().orElseThrow(), legacy.userId());
        }
    }

    @Test
    void unknownUserTypeCodeRoutesToTheRegularMenu() {
        // The IF at app/cbl/COSGN00C.cbl:230 only tests for 'A'; every other code takes the ELSE.
        LegacyUsrsecRecord odd = new LegacyUsrsecRecord("ODDTYPE1", "ODD", "TYPE", "PASSWORD", 'X');
        PasswordHasher hasher = new PasswordHasher(new SecureRandom(), TEST_ITERATIONS);
        store.save(new UsrsecMigration(hasher).migrate(odd));

        VerificationResult result = new CredentialVerificationService(store, hasher)
                .verify("ODDTYPE1", "PASSWORD");
        assertEquals(UserType.USER, result.resolvedUserType().orElseThrow());
        assertEquals("COMEN01C", result.nextProgram().orElseThrow());
    }

    @Test
    void trailingBlanksAreNotSignificantOnIdOrPassword() {
        // SEC-USR-ID PIC X(08) and SEC-USR-PWD PIC X(08) are blank-padded fixed fields; the submitted
        // values are MOVEd into PIC X(08) items at app/cbl/COSGN00C.cbl:132-136.
        assertEquals(SignonOutcome.SUCCESS, service.verify("USER0001", "PASSWORD").outcome());
        assertEquals(SignonOutcome.SUCCESS, service.verify("USER0001   ", "PASSWORD  ").outcome());
        assertEquals(SignonOutcome.SUCCESS, service.verify("USER0001", "PASSWORD ").outcome());

        // A shorter id is a different key, not a prefix match.
        assertEquals(SignonOutcome.USER_NOT_FOUND, service.verify("USER000", "PASSWORD").outcome());
        // Leading blanks are significant in COBOL: ' USER0001' would not be the same key.
        assertEquals(SignonOutcome.USER_NOT_FOUND, service.verify(" USER000", "PASSWORD").outcome());
    }

    @Test
    void submittedValuesAreUpperCasedAsTheCobolDoes() {
        // FUNCTION UPPER-CASE at app/cbl/COSGN00C.cbl:132 and :135 applies to the id and the password.
        assertEquals(SignonOutcome.SUCCESS, service.verify("user0001", "password").outcome());
        assertEquals(SignonOutcome.SUCCESS, service.verify("User0001", "PassWord").outcome());
    }

    @Test
    void blankCredentialsAreTreatedAsAnUnknownUser() {
        // The COBOL rejects blanks before the read (app/cbl/COSGN00C.cbl:118-127); the store never
        // holds a blank key, so the verification service reports not-found rather than a match.
        assertEquals(SignonOutcome.USER_NOT_FOUND, service.verify("        ", "PASSWORD").outcome());
        assertEquals(SignonOutcome.USER_NOT_FOUND, service.verify(null, null).outcome());
    }

    @Test
    void submittedValuesLongerThanTheFieldAreTruncatedNotRejected() {
        // A COBOL MOVE into PIC X(08) truncates on the right; the 3270 map cannot send more than 8.
        assertEquals(SignonOutcome.SUCCESS, service.verify("USER0001XYZ", "PASSWORDXYZ").outcome());
    }
}
