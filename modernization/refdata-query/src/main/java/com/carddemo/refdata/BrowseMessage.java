package com.carddemo.refdata;

/**
 * The messages the COBOL browse screen can report about the state of the browse, with their
 * literal texts. Only the paging-related ones exist here; the update/delete confirmations belong
 * to the write path (COTRTUPC), which is not in this slice.
 */
public enum BrowseMessage {

    /** {@code WS-NO-INFO-MESSAGE} — nothing to say (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:237). */
    NONE(""),

    /** {@code WS-MESG-NO-RECORDS-FOUND} (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:253-254). */
    NO_RECORDS_FOUND("No records found for this search condition."),

    /** {@code WS-MESG-NO-MORE-RECORDS} (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:255-256). */
    NO_MORE_RECORDS("No more pages for these search conditions"),

    /** Literal moved to {@code WS-RETURN-MSG} (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:1539-1540). */
    NO_MORE_PAGES_TO_DISPLAY("No more pages to display"),

    /** Literal moved to {@code WS-RETURN-MSG} (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:1534-1536). */
    NO_PREVIOUS_PAGES("No previous pages to display"),

    /** {@code WS-INFORM-REC-ACTIONS} (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:239-240). */
    RECORD_ACTIONS("Type U to update, D to delete any record"),

    /**
     * {@code WS-DB2-ERROR} raised by the backward cursor when it runs out of rows before the page
     * is full: the backward FETCH loop has no {@code SQLCODE +100} branch, so end-of-data falls
     * into {@code WHEN OTHER} (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:1776-1790).
     */
    BACKWARD_CURSOR_ERROR("Error on fetch Cursor C-TR-TYPE-BACKWARD");

    private final String text;

    BrowseMessage(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
