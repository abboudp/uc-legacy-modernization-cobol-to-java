package com.carddemo.signon;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashingTest {

    private static final int TEST_ITERATIONS = 10_000;

    private final PasswordHasher hasher = new PasswordHasher(new SecureRandom(), TEST_ITERATIONS);

    @Test
    void storedHashIsNeverEqualToThePassword() {
        for (LegacyUsrsecRecord legacy : UsrsecFixture.records()) {
            PasswordHash stored = hasher.hash(legacy.clearTextPassword());
            byte[] passwordBytes = legacy.clearTextPassword().getBytes(StandardCharsets.US_ASCII);

            assertFalse(Arrays.equals(passwordBytes, stored.derivedKey()));
            assertFalse(stored.encoded().contains(legacy.clearTextPassword()));
            assertFalse(stored.toString().contains(legacy.clearTextPassword()));
        }
    }

    @Test
    void usersSharingAPasswordGetDifferentStoredHashes() {
        // The property the legacy file lacks entirely: all ten shipped records hold the identical
        // clear-text bytes 'PASSWORD' (app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS, RISK_REGISTER.md R-08).
        List<UserRecord> migrated = new UsrsecMigration(hasher).migrateAll(UsrsecFixture.records());

        Set<String> distinctSalts = migrated.stream()
                .map(u -> Arrays.toString(u.passwordHash().salt()))
                .collect(Collectors.toSet());
        Set<String> distinctKeys = migrated.stream()
                .map(u -> Arrays.toString(u.passwordHash().derivedKey()))
                .collect(Collectors.toSet());

        assertEquals(migrated.size(), distinctSalts.size(), "per-user salt");
        assertEquals(migrated.size(), distinctKeys.size(), "no two users share a stored hash");
    }

    @Test
    void hashVerifiesAndRejects() {
        PasswordHash stored = hasher.hash("PASSWORD");
        assertTrue(hasher.matches("PASSWORD", stored));
        assertFalse(hasher.matches("PASSWORE", stored));
        assertFalse(hasher.matches("", stored));
    }

    @Test
    void encodedFormRoundTrips() {
        PasswordHash stored = hasher.hash("PASSWORD");
        PasswordHash parsed = PasswordHash.parse(stored.encoded());

        assertEquals(PasswordHasher.ALGORITHM, parsed.algorithm());
        assertEquals(TEST_ITERATIONS, parsed.iterations());
        assertTrue(hasher.matches("PASSWORD", parsed));
    }

    @Test
    void saltIsRequiredAndDefensivelyCopied() {
        byte[] salt = new byte[PasswordHasher.SALT_BYTES];
        PasswordHash stored = new PasswordHash(PasswordHasher.ALGORITHM, TEST_ITERATIONS, salt, new byte[] {1, 2, 3});

        salt[0] = 42;
        assertEquals(0, stored.salt()[0]);
        assertThrows(IllegalArgumentException.class,
                () -> new PasswordHash(PasswordHasher.ALGORITHM, TEST_ITERATIONS, new byte[0], new byte[] {1}));
    }

    @Test
    void defaultParametersAreAdaptive() {
        assertEquals("PBKDF2WithHmacSHA256", PasswordHasher.ALGORITHM);
        assertTrue(PasswordHasher.DEFAULT_ITERATIONS >= 210_000, "OWASP 2023 guidance for PBKDF2-HMAC-SHA256");
        assertTrue(PasswordHasher.SALT_BYTES >= 16);
    }

    @Test
    void userIdWiderThanTheCobolFieldIsRejectedByTheStore() {
        // SEC-USR-ID PIC X(08) (app/cpy/CSUSR01Y.cpy:18) is the USRSEC key and is carried in the
        // COMMAREA at app/cpy/COCOM01Y.cpy:25, so nothing downstream can represent a longer id.
        PasswordHash stored = hasher.hash("PASSWORD");
        assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("USER00001", "A", "B", UserType.USER, stored));
        assertThrows(IllegalArgumentException.class,
                () -> new UserRecord("        ", "A", "B", UserType.USER, stored));

        // Trailing blanks are padding, not content: the id is stored canonically.
        assertEquals("USER0001", new UserRecord("USER0001", "A", "B", UserType.USER, stored).userId());
    }

    @Test
    void migrationDoesNotRetainTheClearTextPassword() {
        LegacyUsrsecRecord legacy = UsrsecFixture.records().get(0);
        UserRecord migrated = new UsrsecMigration(hasher).migrate(legacy);

        assertNotEquals(legacy.clearTextPassword(), migrated.passwordHash().encoded());
        assertFalse(migrated.toString().contains(legacy.clearTextPassword()),
                "the migrated record carries no recoverable password");
    }
}
