package com.carddemo.signon;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Adaptive key derivation for the credential store. PBKDF2-HMAC-SHA256 from the JDK is used so this
 * module has no runtime dependencies; the parameters are self-describing in {@link PasswordHash}, so
 * moving to argon2id later is a re-hash on next sign-on, not a schema change.
 *
 * <p>Comparison goes through {@link MessageDigest#isEqual} which is constant-time for equal-length
 * inputs, replacing the clear-text {@code IF SEC-USR-PWD = WS-USER-PWD} at
 * {@code app/cbl/COSGN00C.cbl:223}.
 */
public final class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 210_000;
    public static final int SALT_BYTES = 16;
    public static final int KEY_BITS = 256;

    private final SecureRandom random;
    private final int iterations;

    public PasswordHasher() {
        this(new SecureRandom(), DEFAULT_ITERATIONS);
    }

    public PasswordHasher(SecureRandom random, int iterations) {
        this.random = random;
        this.iterations = iterations;
    }

    /** Hash a password with a freshly generated per-user salt. */
    public PasswordHash hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordHash(ALGORITHM, iterations, salt, deriveKey(password, salt, iterations));
    }

    /** Constant-time verification of a candidate password against a stored hash. */
    public boolean matches(String candidate, PasswordHash stored) {
        if (!ALGORITHM.equals(stored.algorithm())) {
            throw new IllegalArgumentException("unsupported algorithm: " + stored.algorithm());
        }
        byte[] expected = stored.derivedKey();
        byte[] actual = deriveKey(candidate, stored.salt(), stored.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] deriveKey(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }
}
