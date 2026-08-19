package com.carddemo.signon;

import java.util.ArrayList;
import java.util.List;

/**
 * One-shot cutover: a legacy {@code USRSEC} record, clear-text password included, becomes a
 * {@link UserRecord} whose password is a salted derived key. This is how the real cutover has to work
 * — the clear-text field is the only copy of the credential that exists
 * ({@code app/cpy/CSUSR01Y.cpy:21}), so it is read once, hashed, and not retained.
 *
 * <p>After migration the legacy passwords are unrecoverable. That is the point of the exercise, not a
 * limitation of it.
 *
 * <p>The stored hash is derived from the password <em>as held in the file</em>, canonicalised only by
 * dropping the trailing blanks of {@code PIC X(08)}. It is deliberately not upper-cased, because the
 * COBOL does not upper-case the stored side of the compare — only the submitted side, at
 * {@code app/cbl/COSGN00C.cbl:135}. Preserving the asymmetry preserves the authorization outcome.
 */
public final class UsrsecMigration {

    private final PasswordHasher hasher;

    public UsrsecMigration(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    /** Migrate one legacy record. The clear-text password is consumed here and nowhere else. */
    public UserRecord migrate(LegacyUsrsecRecord legacy) {
        String password = CobolField.canonical(legacy.clearTextPassword());
        return new UserRecord(
                legacy.userId(),
                legacy.firstName(),
                legacy.lastName(),
                UserType.fromCode(legacy.userTypeCode()),
                hasher.hash(password));
    }

    public List<UserRecord> migrateAll(List<LegacyUsrsecRecord> legacyRecords) {
        List<UserRecord> migrated = new ArrayList<>(legacyRecords.size());
        for (LegacyUsrsecRecord legacy : legacyRecords) {
            migrated.add(migrate(legacy));
        }
        return migrated;
    }

    /** Migrate a whole {@code USRSEC} file image into a store, in one pass. */
    public int migrateInto(String usrsecFileContents, CredentialStore store) {
        List<UserRecord> migrated = migrateAll(LegacyUsrsecRecord.parseAll(usrsecFileContents));
        migrated.forEach(store::save);
        return migrated.size();
    }
}
