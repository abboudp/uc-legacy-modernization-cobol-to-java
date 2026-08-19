package com.carddemo.refdata;

import java.util.List;
import java.util.Optional;

/**
 * The read half of the transaction-type reference data slice: the query access pattern that
 * {@code app/app-transaction-type-db2/cbl/COTRTLIC.cbl} implements against Db2, and nothing else.
 *
 * <p>Paging is keyset paging over the primary key, because that is what the COBOL does: it browses
 * with two cursors anchored on a last-key-read held in the COMMAREA
 * ({@code C-TR-TYPE-FORWARD}, COTRTLIC.cbl:338-352; {@code C-TR-TYPE-BACKWARD}, :354-368).
 */
public interface TransactionTypeQueryService {

    /**
     * The page the screen shows on entry, and after PF3/ENTER re-display: a forward read from the
     * remembered first key, which is {@code LOW-VALUES} on a fresh COMMAREA
     * (COTRTLIC.cbl:871-875, with {@code WS-START-KEY} initialised at :275 / :500-502).
     */
    BrowsePage firstPage(int pageSize);

    /** PF8 — page down (COTRTLIC.cbl:766-776). */
    BrowsePage nextPage(BrowsePage current);

    /** PF7 — page up (COTRTLIC.cbl:780-790, and the first-page guard at :1532-1536). */
    BrowsePage previousPage(BrowsePage current);

    /**
     * Single-row read of a transaction type by its key, as the update screen does before it
     * displays a row (COTRTUPC.cbl:1475-1481).
     */
    Optional<TransactionType> findTransactionType(String trType);

    /** Single-row read of one type/category pair by its composite primary key. */
    Optional<TransactionTypeCategory> findCategory(String trcTypeCode, String trcTypeCategory);

    /**
     * The categories of one transaction type, in {@code TRC_TYPE_CODE, TRC_TYPE_CATEGORY} order —
     * the order the unload job reads them in (TRANEXTR.jcl:114-116).
     */
    List<TransactionTypeCategory> categoriesOfType(String trcTypeCode);

    /**
     * {@code SELECT COUNT(1) FROM CARDDEMO.TRANSACTION_TYPE} — the unfiltered form of the
     * program's own count query (COTRTLIC.cbl:1803-1815).
     */
    int countTransactionTypes();

    /** Row count of the category table; no COBOL counterpart, used to pin the seeded data. */
    int countTransactionTypeCategories();
}
