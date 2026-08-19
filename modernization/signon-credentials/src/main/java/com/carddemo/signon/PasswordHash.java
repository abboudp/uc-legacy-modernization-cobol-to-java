package com.carddemo.signon;

import java.util.Base64;
import java.util.Objects;

/**
 * A stored credential: algorithm parameters, a per-user salt and the derived key. This replaces
 * {@code SEC-USR-PWD PIC X(08)} ({@code app/cpy/CSUSR01Y.cpy:21}); no clear-text password is held
 * anywhere in the target model.
 */
public record PasswordHash(String algorithm, int iterations, byte[] salt, byte[] derivedKey) {

    public PasswordHash {
        Objects.requireNonNull(algorithm, "algorithm");
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        if (salt == null || salt.length == 0) {
            throw new IllegalArgumentException("a per-user salt is required");
        }
        if (derivedKey == null || derivedKey.length == 0) {
            throw new IllegalArgumentException("derivedKey is required");
        }
        salt = salt.clone();
        derivedKey = derivedKey.clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] derivedKey() {
        return derivedKey.clone();
    }

    /** Self-describing storage form, so the parameters can be raised later without a flag day. */
    public String encoded() {
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return String.join("$", "", algorithm, Integer.toString(iterations),
                encoder.encodeToString(salt), encoder.encodeToString(derivedKey));
    }

    public static PasswordHash parse(String encoded) {
        String[] parts = encoded.split("\\$");
        if (parts.length != 5 || !parts[0].isEmpty()) {
            throw new IllegalArgumentException("not an encoded password hash");
        }
        Base64.Decoder decoder = Base64.getDecoder();
        return new PasswordHash(parts[1], Integer.parseInt(parts[2]), decoder.decode(parts[3]), decoder.decode(parts[4]));
    }

    /** Deliberately never renders the hash or salt. */
    @Override
    public String toString() {
        return "PasswordHash[" + algorithm + ", iterations=" + iterations + "]";
    }
}
