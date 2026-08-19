package com.carddemo.refdata;

import java.util.List;

/**
 * One page of the transaction-type browse, plus the anchors and flags the COBOL keeps in its
 * COMMAREA between screens ({@code WS-CA-PAGING-VARIABLES},
 * app/app-transaction-type-db2/cbl/COTRTLIC.cbl:397-410).
 *
 * <p>This is the whole paging state: there is no offset and no cursor held open across
 * interactions. Every page is re-derived from {@link #firstKey()} or {@link #lastKey()} by opening
 * a fresh cursor, exactly as the CICS pseudo-conversational program does
 * ({@code 9400-OPEN-FORWARD-CURSOR} at :1942, {@code 9450-CLOSE-FORWARD-CURSOR} at :1970 — the
 * cursor cannot outlive the transaction).
 *
 * @param rows           the rows displayed, in {@code TR_TYPE} order
 * @param firstKey       {@code WS-CA-FIRST-TR-CODE} — key of the first row on this page (:401)
 * @param lastKey        {@code WS-CA-LAST-TR-CODE} — the forward anchor for the next page (:399);
 *                       when a next page exists this is the key of the *peeked* first row of that
 *                       next page, not the last row of this one (:1673)
 * @param screenNumber   {@code WS-CA-SCREEN-NUM}, 1-based; 1 means first page (:403-404)
 * @param nextPageExists {@code WS-CA-NEXT-PAGE-IND} (:408-410)
 * @param lastPageShown  {@code WS-CA-LAST-PAGE-DISPLAYED} — set once the user has been told the
 *                       browse is at its end (:405-407, :1546-1549)
 * @param pageSize       {@code WS-MAX-SCREEN-LINES}, 7 on the real screen (:60)
 * @param message        what the screen would report
 */
public record BrowsePage(
        List<BrowseRow> rows,
        String firstKey,
        String lastKey,
        int screenNumber,
        boolean nextPageExists,
        boolean lastPageShown,
        int pageSize,
        BrowseMessage message) {

    public BrowsePage {
        rows = List.copyOf(rows);
    }

    public List<String> keys() {
        return rows.stream().map(BrowseRow::key).toList();
    }

    public boolean isFirstPage() {
        return screenNumber == 1;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    BrowsePage withMessage(BrowseMessage newMessage) {
        return new BrowsePage(rows, firstKey, lastKey, screenNumber, nextPageExists, lastPageShown,
                pageSize, newMessage);
    }

    BrowsePage withLastPageShown() {
        return new BrowsePage(rows, firstKey, lastKey, screenNumber, nextPageExists, true, pageSize,
                message);
    }
}
