package com.carddemo.signon;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the field offsets this module assumes against the shipped {@code USRSEC} data, so a wrong
 * reading of {@code app/cpy/CSUSR01Y.cpy} fails here rather than silently later.
 */
class UsrsecFixtureLayoutTest {

    @Test
    void recordLengthMatchesCopybookWidths() {
        // 8 + 20 + 20 + 8 + 1 + 23 — app/cpy/CSUSR01Y.cpy:18-23.
        assertEquals(80, CobolField.RECORD_LENGTH);
        assertEquals(0, UsrsecFixture.contents().length() % 80);
    }

    @Test
    void fixtureHoldsTheTenShippedUsers() {
        List<LegacyUsrsecRecord> records = UsrsecFixture.records();
        assertEquals(10, records.size(), "800 bytes at 80 bytes per record");

        assertEquals(
                List.of("ADMIN001", "ADMIN002", "ADMIN003", "ADMIN004", "ADMIN005",
                        "USER0001", "USER0002", "USER0003", "USER0004", "USER0005"),
                records.stream().map(LegacyUsrsecRecord::userId).collect(Collectors.toList()));

        assertEquals("AAAAAUUUUU",
                records.stream().map(r -> String.valueOf(r.userTypeCode())).collect(Collectors.joining()),
                "SEC-USR-TYPE for the shipped users");
    }

    @Test
    void firstRecordDecodesFieldByField() {
        LegacyUsrsecRecord first = UsrsecFixture.records().get(0);
        assertEquals("ADMIN001", first.userId());
        assertEquals("MARGARET", first.firstName());
        assertEquals("GOLD", first.lastName());
        assertEquals("PASSWORD", first.clearTextPassword());
        assertEquals('A', first.userTypeCode());
    }

    @Test
    void everyShippedUserHasTheSameClearTextPassword() {
        // The property this slice removes: the legacy file has no salt, so identical passwords are
        // identical bytes on disk. See RISK_REGISTER.md R-08.
        assertTrue(UsrsecFixture.records().stream().allMatch(r -> "PASSWORD".equals(r.clearTextPassword())));
    }
}
