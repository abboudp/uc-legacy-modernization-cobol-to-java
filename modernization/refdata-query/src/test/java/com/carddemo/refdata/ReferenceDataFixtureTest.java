package com.carddemo.refdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Grounds the seeded data in the repository fixtures and in the copybook layouts.
 */
class ReferenceDataFixtureTest {

    /**
     * Row-count pins. A future change to the fixtures fails here rather than silently changing the
     * meaning of every paging assertion below.
     */
    @Test
    void fixtureRowCountsArePinned() {
        assertEquals(7, RefDataTestDatabase.types().size(),
                "app/data/ASCII/trantype.txt row count");
        assertEquals(18, RefDataTestDatabase.categories().size(),
                "app/data/ASCII/trancatg.txt row count");

        JdbcTransactionTypeQueryService service =
                new JdbcTransactionTypeQueryService(RefDataTestDatabase.seeded());
        assertEquals(7, service.countTransactionTypes());
        assertEquals(18, service.countTransactionTypeCategories());
    }

    /** Every fixture record is exactly 60 bytes: {@code RECLN = 60} in both copybooks. */
    @Test
    void everyFixtureRecordIsSixtyBytes() throws IOException {
        for (String fixture : List.of(RefDataTestDatabase.TRANTYPE_FIXTURE,
                RefDataTestDatabase.TRANCATG_FIXTURE)) {
            List<String> lines = Files.readAllLines(
                    RefDataTestDatabase.fixture(fixture), StandardCharsets.US_ASCII);
            for (String line : lines) {
                String record = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
                if (!record.isEmpty()) {
                    assertEquals(ReferenceDataLoader.RECORD_LENGTH, record.length(),
                            fixture + " record [" + record + "]");
                }
            }
        }
    }

    /**
     * The type record is {@code X(02)} + {@code X(50)} + {@code FILLER X(08)}
     * (app/cpy/CVTRA03Y.cpy:5-7): the two-character code and the description land in the columns
     * the DCLGEN declares, and the filler is not data.
     */
    @Test
    void typeFixtureMatchesCvtra03yLayout() {
        List<TransactionType> types = RefDataTestDatabase.types();
        assertEquals(new TransactionType("01", "Purchase"), types.get(0));
        assertEquals(new TransactionType("07", "Adjustment"), types.get(types.size() - 1));
        for (TransactionType type : types) {
            assertEquals(2, type.trType().length(), "TR_TYPE is CHAR(2)");
            assertTrue(type.trDescription().length() <= 50, "TR_DESCRIPTION is VARCHAR(50)");
        }
    }

    /**
     * The category record is {@code X(02)} + {@code 9(04)} + {@code X(50)} + {@code FILLER X(04)}
     * (app/cpy/CVTRA04Y.cpy:6-9). The four-digit category is kept as characters with its leading
     * zeros, which is what the {@code CHAR(4)} column stores.
     */
    @Test
    void categoryFixtureMatchesCvtra04yLayout() {
        List<TransactionTypeCategory> categories = RefDataTestDatabase.categories();
        assertEquals(new TransactionTypeCategory("01", "0001", "Regular Sales Draft"),
                categories.get(0));
        assertEquals(new TransactionTypeCategory("07", "0001", "Sales draft credit adjustment"),
                categories.get(categories.size() - 1));

        Set<String> typeCodes = Set.copyOf(
                RefDataTestDatabase.types().stream().map(TransactionType::trType).toList());
        for (TransactionTypeCategory category : categories) {
            assertEquals(4, category.trcTypeCategory().length(), "TRC_TYPE_CATEGORY is CHAR(4)");
            assertTrue(category.trcTypeCategory().matches("\\d{4}"),
                    "TRC_TYPE_CATEGORY holds the digits of PIC 9(04)");
            // The foreign key of DB2CREAT.ctl:96-99 must already hold in the fixture data,
            // otherwise the load steps of CREADB21.jcl could not have run.
            assertTrue(typeCodes.contains(category.trcTypeCode()),
                    "TRC_TYPE_CODE " + category.trcTypeCode() + " has no parent TR_TYPE");
        }
    }
}
