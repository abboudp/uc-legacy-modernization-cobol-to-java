package com.carddemo.refdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Single-row reads by key. The shape comes from the estate's only singleton {@code SELECT} on the
 * table, {@code 9100-GET-TRANSACTION-TYPE}
 * (app/app-transaction-type-db2/cbl/COTRTUPC.cbl:1475-1482): equality on the whole key, with
 * {@code SQLCODE +100} meaning "not found" rather than an error (:1489-1494) — hence
 * {@link java.util.Optional} rather than an exception. The browse screen itself only ever reaches a
 * single row through the same predicate on its UPDATE and DELETE (COTRTLIC.cbl:1853, :1906).
 *
 * <p>No COBOL program in the estate reads {@code TRANSACTION_TYPE_CATEGORY} at all — it is read only
 * by the unload job (TRANEXTR.jcl:107-116) — so the category reads follow that job's key order and
 * the table's primary key (ddl/TRNTYCAT.ddl), not a program. See the README.
 */
class SingleRowLookupTest {

    private TransactionTypeQueryService service;

    @BeforeEach
    void setUp() {
        service = new JdbcTransactionTypeQueryService(RefDataTestDatabase.seeded());
    }

    @Test
    void transactionTypeIsFoundByItsTwoCharacterKey() {
        TransactionType type = service.findTransactionType("01").orElseThrow();
        assertEquals("01", type.trType());
        assertEquals("Purchase", type.trDescription());
        assertEquals("Adjustment", service.findTransactionType("07").orElseThrow().trDescription());
    }

    @Test
    void unknownTransactionTypeIsEmptyRatherThanAnError() {
        assertTrue(service.findTransactionType("99").isEmpty());
        assertTrue(service.findCategory("99", "0001").isEmpty());
        assertTrue(service.findCategory("01", "9999").isEmpty());
        assertEquals(List.of(), service.categoriesOfType("99"));
    }

    @Test
    void categoryIsFoundByItsCompositeKey() {
        TransactionTypeCategory category = service.findCategory("04", "0002").orElseThrow();
        assertEquals("04", category.trcTypeCode());
        assertEquals("0002", category.trcTypeCategory());
        assertEquals("Online purchase authorization", category.trcCatData());
    }

    /**
     * {@code TRC_TYPE_CATEGORY} stays a string: the DCLGEN host variable is {@code PIC X(4)}
     * (app/app-transaction-type-db2/dcl/DCLTRCAT.dcl:42) even though the VSAM copybook declares
     * {@code PIC 9(04)} (app/cpy/CVTRA04Y.cpy:7). Reading it as a number would lose the leading
     * zeroes the key is stored and compared with.
     */
    @Test
    void categoryKeyKeepsItsLeadingZeroes() {
        assertEquals("0001", service.findCategory("07", "0001").orElseThrow().trcTypeCategory());
        assertTrue(service.findCategory("07", "1").isEmpty(),
                "'1' is not the same key as '0001'");
    }

    @Test
    void categoriesOfATypeComeBackInKeyOrder() {
        assertEquals(List.of("0001", "0002", "0003"),
                service.categoriesOfType("02").stream()
                        .map(TransactionTypeCategory::trcTypeCategory).toList());
        assertEquals(List.of("Cash payment", "Electronic payment", "Check payment"),
                service.categoriesOfType("02").stream()
                        .map(TransactionTypeCategory::trcCatData).toList());
    }
}
