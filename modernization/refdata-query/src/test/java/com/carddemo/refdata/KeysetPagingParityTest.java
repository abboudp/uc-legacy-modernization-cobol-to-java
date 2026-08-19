package com.carddemo.refdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Paging parity with {@code 8000-READ-FORWARD} and {@code 8100-READ-BACKWARDS} in
 * {@code app/app-transaction-type-db2/cbl/COTRTLIC.cbl}.
 *
 * <p>Page size 3 is used for most cases so the 7 seeded types make three pages, the last of which
 * is short. The real screen holds 7 rows ({@code WS-MAX-SCREEN-LINES}, COTRTLIC.cbl:60), which with
 * this data is a single page — exercised separately below.
 */
class KeysetPagingParityTest {

    private static final int PAGE = 3;

    private TransactionTypeQueryService service;

    @BeforeEach
    void setUp() {
        DataSource dataSource = RefDataTestDatabase.seeded();
        service = new JdbcTransactionTypeQueryService(dataSource);
    }

    /** {@code ORDER BY TR_TYPE} on the forward cursor (COTRTLIC.cbl:351). */
    @Test
    void pagesAreOrderedByPrimaryKeyAscending() {
        List<String> browsed = forwardKeys();
        List<String> sorted = new ArrayList<>(browsed);
        sorted.sort(Comparator.naturalOrder());
        assertEquals(sorted, browsed, "browse order must be the cursor's ORDER BY TR_TYPE");
        assertEquals(List.of("01", "02", "03", "04", "05", "06", "07"), browsed);
    }

    @Test
    void forwardPagingWalksTheWholeTableInPageSizedChunks() {
        BrowsePage page = service.firstPage(PAGE);
        assertEquals(List.of("01", "02", "03"), page.keys());
        assertEquals(1, page.screenNumber());
        assertTrue(page.nextPageExists());

        page = service.nextPage(page);
        assertEquals(List.of("04", "05", "06"), page.keys());
        assertEquals(2, page.screenNumber());
        assertTrue(page.nextPageExists());

        page = service.nextPage(page);
        assertEquals(List.of("07"), page.keys());
        assertEquals(3, page.screenNumber());
        assertFalse(page.nextPageExists());
    }

    /**
     * The forward anchor is the key of the row fetched one past the end of the page
     * (COTRTLIC.cbl:1657-1673), so the next page starts exactly where this one stopped: the
     * cursor's {@code TR_TYPE >= :WS-START-KEY} (:343) is inclusive and the pages tile without
     * overlap or gap.
     */
    @Test
    void nextPageAnchorIsTheFirstRowOfTheNextPage() {
        BrowsePage first = service.firstPage(PAGE);
        assertEquals("04", first.lastKey());
        assertEquals("04", service.nextPage(first).keys().get(0));
        assertEquals("01", first.firstKey());
    }

    /** Paging forward then back yields exactly the same pages in reverse. */
    @Test
    void backwardPagesAreTheExactInverseOfForwardPages() {
        List<List<String>> forward = new ArrayList<>();
        BrowsePage page = service.firstPage(PAGE);
        forward.add(page.keys());
        while (page.nextPageExists()) {
            page = service.nextPage(page);
            forward.add(page.keys());
        }

        List<List<String>> backward = new ArrayList<>();
        backward.add(page.keys());
        while (!page.isFirstPage()) {
            page = service.previousPage(page);
            backward.add(page.keys());
        }

        List<List<String>> reversedForward = new ArrayList<>(forward);
        java.util.Collections.reverse(reversedForward);
        assertEquals(reversedForward, backward);
        assertEquals(1, page.screenNumber(), "walking back must land on screen 1 again");
        assertEquals(BrowseMessage.NONE, page.message());
    }

    /**
     * A backward page adopts the page it came from as its forward anchor
     * ({@code MOVE WS-CA-FIRST-TTYPEKEY TO WS-CA-LAST-TTYPEKEY}, COTRTLIC.cbl:1731), so PF7 then
     * PF8 returns to the page the user was on.
     */
    @Test
    void backwardThenForwardReturnsToTheSamePage() {
        BrowsePage second = service.nextPage(service.firstPage(PAGE));
        BrowsePage back = service.previousPage(second);
        assertEquals(List.of("01", "02", "03"), back.keys());
        assertEquals(second.keys(), service.nextPage(back).keys());
    }

    /** The first page is re-derived from a key, not an offset, so repeating it is idempotent. */
    @Test
    void firstPageIsStable() {
        BrowsePage first = service.firstPage(PAGE);
        assertEquals(first.keys(), service.firstPage(PAGE).keys());
        assertEquals(first.firstKey(), service.firstPage(PAGE).firstKey());
        assertEquals(first.lastKey(), service.firstPage(PAGE).lastKey());
    }

    /**
     * PF7 on page 1 does not read backwards at all: the guard at COTRTLIC.cbl:1532-1535 reports
     * "No previous pages to display" and the same page is re-displayed (:726-734).
     */
    @Test
    void previousPageOnFirstPageRedisplaysAndSaysSo() {
        BrowsePage first = service.firstPage(PAGE);
        BrowsePage again = service.previousPage(first);
        assertEquals(first.keys(), again.keys());
        assertEquals(BrowseMessage.NO_PREVIOUS_PAGES, again.message());
        assertEquals("No previous pages to display", again.message().text());
    }

    /**
     * End of data is signalled by the peek FETCH returning {@code SQLCODE +100}, which sets
     * {@code CA-NEXT-PAGE-NOT-EXISTS} and, when PF8 was pressed, {@code WS-MESG-NO-MORE-RECORDS}
     * (COTRTLIC.cbl:1674-1680, :1698-1701). A further PF8 latches {@code CA-LAST-PAGE-SHOWN}
     * (:1541-1549) and reports "No more pages to display" (:1536-1540); the rows do not move.
     */
    @Test
    void endOfDataIsReportedTheWayTheCobolReportsIt() {
        BrowsePage last = service.nextPage(service.nextPage(service.firstPage(PAGE)));
        assertFalse(last.nextPageExists());
        assertEquals(BrowseMessage.NO_MORE_RECORDS, last.message());
        assertEquals("No more pages for these search conditions", last.message().text());

        BrowsePage pastEnd = service.nextPage(last);
        assertEquals(last.keys(), pastEnd.keys(), "PF8 past the end must not move the page");
        assertTrue(pastEnd.lastPageShown());
        assertEquals(BrowseMessage.NO_MORE_RECORDS, pastEnd.message());

        BrowsePage stillPastEnd = service.nextPage(pastEnd);
        assertEquals(last.keys(), stillPastEnd.keys());
        assertEquals(BrowseMessage.NO_MORE_PAGES_TO_DISPLAY, stillPastEnd.message());
        assertEquals("No more pages to display", stillPastEnd.message().text());
    }

    /**
     * On a short page the forward anchor is spaces, not the last row read: the host variable is
     * re-initialised before each FETCH (COTRTLIC.cbl:1624) and the {@code +100} branch moves that
     * initialised value into {@code WS-CA-LAST-TR-CODE} (:1696-1697).
     */
    @Test
    void shortPageLeavesTheForwardAnchorBlank() {
        BrowsePage last = service.nextPage(service.nextPage(service.firstPage(PAGE)));
        assertEquals(JdbcTransactionTypeQueryService.SPACES_KEY, last.lastKey());
        assertEquals("07", last.firstKey());
    }

    /**
     * With the screen's own page size the 7 seeded types are one full page with no next page: the
     * peek FETCH after row 7 hits end of data.
     */
    @Test
    void screenSizedPageHoldsAllSevenSeededTypes() {
        BrowsePage page = service.firstPage(7);
        assertEquals(7, page.rows().size());
        assertFalse(page.nextPageExists());
        assertEquals(BrowseMessage.NONE, page.message());
    }

    /** An empty result set reports {@code WS-MESG-NO-RECORDS-FOUND} (COTRTLIC.cbl:1702-1705). */
    @Test
    void emptyTableReportsNoRecordsFound() {
        DataSource empty = RefDataTestDatabase.emptyDatabase();
        BrowsePage page = new JdbcTransactionTypeQueryService(empty).firstPage(PAGE);
        assertTrue(page.isEmpty());
        assertEquals(BrowseMessage.NO_RECORDS_FOUND, page.message());
        assertEquals("No records found for this search condition.", page.message().text());
    }

    /**
     * Quirk of the source, reproduced deliberately: the backward FETCH loop has no
     * {@code SQLCODE +100} branch, so running out of rows before the page is full falls into
     * {@code WHEN OTHER} and is reported as a Db2 error (COTRTLIC.cbl:1776-1790). It cannot happen
     * in a normal browse because PF7 is guarded on the first page (:780-781), but it is what the
     * program does.
     */
    @Test
    void backwardCursorTreatsEndOfDataAsAnError() {
        BrowsePage synthetic = new BrowsePage(List.of(), "02", "02", 2, true, false, PAGE,
                BrowseMessage.NONE);
        BrowsePage result = service.previousPage(synthetic);
        assertEquals(List.of("01"), result.keys());
        assertEquals(BrowseMessage.BACKWARD_CURSOR_ERROR, result.message());
    }

    /** Each browse row carries the categories of its type, in unload order (TRANEXTR.jcl:114-116). */
    @Test
    void browseRowsCarryTheirCategoriesInKeyOrder() {
        BrowsePage first = service.firstPage(PAGE);
        BrowseRow purchase = first.rows().get(0);
        assertEquals("01", purchase.key());
        assertEquals(List.of("0001", "0002", "0003", "0004", "0005"),
                purchase.categories().stream()
                        .map(TransactionTypeCategory::trcTypeCategory).toList());
        assertEquals("Regular Sales Draft", purchase.categories().get(0).trcCatData());
    }

    private List<String> forwardKeys() {
        List<String> keys = new ArrayList<>();
        BrowsePage page = service.firstPage(PAGE);
        keys.addAll(page.keys());
        while (page.nextPageExists()) {
            page = service.nextPage(page);
            keys.addAll(page.keys());
        }
        return keys;
    }
}
