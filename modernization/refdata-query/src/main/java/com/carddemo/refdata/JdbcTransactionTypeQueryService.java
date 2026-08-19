package com.carddemo.refdata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * JDBC implementation of the browse. The two paged queries are transcriptions of the COBOL cursor
 * declarations with the filter host variables dropped (the screen filters belong to the BMS layer,
 * which is not in this slice), and the fetch loops are transcriptions of {@code 8000-READ-FORWARD}
 * and {@code 8100-READ-BACKWARDS}.
 *
 * <p>Rows are pulled one {@link ResultSet#next()} at a time and the statement is closed at the end
 * of the page, mirroring {@code OPEN}/{@code FETCH}/{@code CLOSE} per interaction
 * (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:1609-1610, :1721-1722). There is deliberately no
 * {@code LIMIT} or {@code OFFSET} anywhere in this class; see README.
 */
public final class JdbcTransactionTypeQueryService implements TransactionTypeQueryService {

    /**
     * {@code WS-START-KEY} is {@code PIC X(02)} and is left at {@code LOW-VALUES} on a fresh
     * COMMAREA (COTRTLIC.cbl:275, :500-502), so the first forward read starts below every stored
     * key. The empty string is the portable equivalent for {@code TR_TYPE >= ?}.
     */
    private static final String LOW_VALUES = "";

    /**
     * What {@code WS-CA-LAST-TR-CODE} contains after a short forward page: the host variable is
     * re-initialised before every FETCH (COTRTLIC.cbl:1624), so the {@code SQLCODE +100} branch
     * moves spaces, not the last row read, into the anchor (:1696-1697).
     */
    static final String SPACES_KEY = "  ";

    /** {@code C-TR-TYPE-FORWARD} (COTRTLIC.cbl:338-352), filter predicates dropped. */
    private static final String FORWARD_SQL = """
            SELECT TR_TYPE, TR_DESCRIPTION
              FROM CARDDEMO.TRANSACTION_TYPE
             WHERE TR_TYPE >= ?
             ORDER BY TR_TYPE""";

    /** {@code C-TR-TYPE-BACKWARD} (COTRTLIC.cbl:354-368), filter predicates dropped. */
    private static final String BACKWARD_SQL = """
            SELECT TR_TYPE, TR_DESCRIPTION
              FROM CARDDEMO.TRANSACTION_TYPE
             WHERE TR_TYPE < ?
             ORDER BY TR_TYPE DESC""";

    private static final String TYPE_BY_KEY_SQL = """
            SELECT TR_TYPE, TR_DESCRIPTION
              FROM CARDDEMO.TRANSACTION_TYPE
             WHERE TR_TYPE = ?""";

    private static final String CATEGORY_BY_KEY_SQL = """
            SELECT TRC_TYPE_CODE, TRC_TYPE_CATEGORY, TRC_CAT_DATA
              FROM CARDDEMO.TRANSACTION_TYPE_CATEGORY
             WHERE TRC_TYPE_CODE = ?
               AND TRC_TYPE_CATEGORY = ?""";

    /** Category order per the unload job's ORDER BY (TRANEXTR.jcl:114-116). */
    private static final String CATEGORIES_OF_TYPE_SQL = """
            SELECT TRC_TYPE_CODE, TRC_TYPE_CATEGORY, TRC_CAT_DATA
              FROM CARDDEMO.TRANSACTION_TYPE_CATEGORY
             WHERE TRC_TYPE_CODE = ?
             ORDER BY TRC_TYPE_CODE, TRC_TYPE_CATEGORY""";

    private final DataSource dataSource;

    public JdbcTransactionTypeQueryService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public BrowsePage firstPage(int pageSize) {
        return forwardPage(LOW_VALUES, pageSize, 0, false);
    }

    @Override
    public BrowsePage nextPage(BrowsePage current) {
        if (current.nextPageExists()) {
            // PF8 with a next page: anchor on WS-CA-LAST-TR-CODE and bump the screen number
            // (COTRTLIC.cbl:766-772).
            return forwardPage(current.lastKey(), current.pageSize(), current.screenNumber() + 1,
                    true);
        }
        // No next page: the PF8 branch guard at :766-767 fails and control reaches WHEN OTHER
        // (:870-877), which re-reads the *same* page forward from WS-CA-FIRST-TR-CODE. The first
        // such PF8 reports "no more records" and latches CA-LAST-PAGE-SHOWN (:1541-1549); a second
        // one reports "No more pages to display" (:1536-1540).
        BrowsePage samePage = forwardPage(current.firstKey(), current.pageSize(),
                current.screenNumber(), true);
        BrowseMessage message = current.lastPageShown()
                ? BrowseMessage.NO_MORE_PAGES_TO_DISPLAY
                : BrowseMessage.NO_MORE_RECORDS;
        return samePage.withLastPageShown().withMessage(message);
    }

    @Override
    public BrowsePage previousPage(BrowsePage current) {
        if (current.isFirstPage()) {
            // PF7 on page 1: re-read the same page forward and say so (:726-734).
            return forwardPage(current.firstKey(), current.pageSize(), current.screenNumber(), false)
                    .withMessage(BrowseMessage.NO_PREVIOUS_PAGES); // :1532-1535
        }
        return backwardPage(current.firstKey(), current.pageSize(), current.screenNumber() - 1);
    }

    /**
     * {@code 8000-READ-FORWARD} (COTRTLIC.cbl:1603-1723). Reads up to {@code pageSize} rows from
     * {@code startKey} inclusive, then fetches one row beyond to decide whether a next page exists
     * — that extra row's key becomes the anchor for the next page (:1657-1673).
     */
    private BrowsePage forwardPage(String startKey, int pageSize, int screenNumberIn,
                                   boolean pageDownRequested) {
        requirePageSize(pageSize);
        List<TransactionType> rows = new ArrayList<>(pageSize);
        String firstKey = startKey;
        String lastKey = startKey;
        boolean nextPageExists = false;
        int screenNumber = screenNumberIn;
        BrowseMessage message = BrowseMessage.NONE;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FORWARD_SQL)) {
            statement.setString(1, startKey);
            try (ResultSet cursor = statement.executeQuery()) {
                while (rows.size() < pageSize && cursor.next()) {
                    TransactionType row = readType(cursor);
                    rows.add(row);
                    if (rows.size() == 1) {
                        firstKey = row.trType();
                        if (screenNumber == 0) {
                            screenNumber = 1; // :1644-1648
                        }
                    }
                }
                if (rows.size() == pageSize) {
                    lastKey = rows.get(pageSize - 1).trType(); // :1659
                    if (cursor.next()) {
                        nextPageExists = true;
                        lastKey = readType(cursor).trType(); // :1671-1673
                    } else if (pageDownRequested) {
                        message = BrowseMessage.NO_MORE_RECORDS; // :1674-1680
                    }
                } else {
                    lastKey = SPACES_KEY; // :1696-1697, see SPACES_KEY
                    if (pageDownRequested) {
                        message = BrowseMessage.NO_MORE_RECORDS; // :1698-1701
                    }
                    if (screenNumber <= 1 && rows.isEmpty()) {
                        screenNumber = Math.max(screenNumber, 1);
                        message = BrowseMessage.NO_RECORDS_FOUND; // :1702-1705
                    }
                }
            }
        } catch (SQLException e) {
            throw new RefDataQueryException("C-TR-TYPE-FORWARD fetch", e);
        }
        return new BrowsePage(attachCategories(rows), firstKey, lastKey, screenNumber,
                nextPageExists, false, pageSize, message);
    }

    /**
     * {@code 8100-READ-BACKWARDS} (COTRTLIC.cbl:1727-1799). Fills the screen array from the bottom
     * up with rows strictly below the anchor in descending key order, so the page ends up in
     * ascending order again (:1762-1774), and adopts the anchor as the forward anchor (:1731).
     */
    private BrowsePage backwardPage(String anchorKey, int pageSize, int screenNumber) {
        requirePageSize(pageSize);
        TransactionType[] slots = new TransactionType[pageSize];
        int slot = pageSize; // WS-ROW-NUMBER, 1-based (:1735-1737)
        String firstKey = anchorKey;
        BrowseMessage message = BrowseMessage.NONE;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(BACKWARD_SQL)) {
            statement.setString(1, anchorKey);
            try (ResultSet cursor = statement.executeQuery()) {
                while (slot > 0) {
                    if (!cursor.next()) {
                        // The backward loop has no SQLCODE +100 branch: end-of-data before the page
                        // is full lands in WHEN OTHER and is reported as a Db2 error (:1776-1790).
                        message = BrowseMessage.BACKWARD_CURSOR_ERROR;
                        break;
                    }
                    TransactionType row = readType(cursor);
                    slots[slot - 1] = row;
                    slot--;
                    if (slot == 0) {
                        firstKey = row.trType(); // :1770-1773
                    }
                }
            }
        } catch (SQLException e) {
            throw new RefDataQueryException("C-TR-TYPE-BACKWARD fetch", e);
        }

        List<TransactionType> rows = Arrays.stream(slots).filter(java.util.Objects::nonNull).toList();
        // CA-NEXT-PAGE-EXISTS is set unconditionally before the backward read (:1738): having come
        // from a later page, there is by construction something ahead of this one.
        return new BrowsePage(attachCategories(rows), firstKey, anchorKey, screenNumber, true, false,
                pageSize, message);
    }

    @Override
    public Optional<TransactionType> findTransactionType(String trType) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(TYPE_BY_KEY_SQL)) {
            statement.setString(1, trType);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readType(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RefDataQueryException("TRANSACTION_TYPE single-row read", e);
        }
    }

    @Override
    public Optional<TransactionTypeCategory> findCategory(String trcTypeCode, String trcTypeCategory) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CATEGORY_BY_KEY_SQL)) {
            statement.setString(1, trcTypeCode);
            statement.setString(2, trcTypeCategory);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCategory(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RefDataQueryException("TRANSACTION_TYPE_CATEGORY single-row read", e);
        }
    }

    @Override
    public List<TransactionTypeCategory> categoriesOfType(String trcTypeCode) {
        List<TransactionTypeCategory> categories = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CATEGORIES_OF_TYPE_SQL)) {
            statement.setString(1, trcTypeCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    categories.add(readCategory(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new RefDataQueryException("TRANSACTION_TYPE_CATEGORY read by type", e);
        }
        return List.copyOf(categories);
    }

    @Override
    public int countTransactionTypes() {
        return count("CARDDEMO.TRANSACTION_TYPE");
    }

    @Override
    public int countTransactionTypeCategories() {
        return count("CARDDEMO.TRANSACTION_TYPE_CATEGORY");
    }

    private int count(String table) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("SELECT COUNT(1) FROM " + table);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException e) {
            throw new RefDataQueryException("COUNT(1) on " + table, e);
        }
    }

    private List<BrowseRow> attachCategories(List<TransactionType> types) {
        Map<String, List<TransactionTypeCategory>> byType = new LinkedHashMap<>();
        for (TransactionType type : types) {
            byType.put(type.trType(), categoriesOfType(type.trType()));
        }
        return types.stream()
                .map(type -> new BrowseRow(type, byType.get(type.trType())))
                .toList();
    }

    private static TransactionType readType(ResultSet resultSet) throws SQLException {
        // TR_TYPE is CHAR(2) and TR_DESCRIPTION is VARCHAR(50): the fixed-width column is returned
        // padded, the varying one is not. Trailing blanks are stripped from both so that a value
        // read here equals the value the unload job writes into the 60-byte replica record.
        return new TransactionType(rtrim(resultSet.getString("TR_TYPE")),
                rtrim(resultSet.getString("TR_DESCRIPTION")));
    }

    private static TransactionTypeCategory readCategory(ResultSet resultSet) throws SQLException {
        return new TransactionTypeCategory(rtrim(resultSet.getString("TRC_TYPE_CODE")),
                rtrim(resultSet.getString("TRC_TYPE_CATEGORY")),
                rtrim(resultSet.getString("TRC_CAT_DATA")));
    }

    private static String rtrim(String value) {
        return value == null ? null : value.stripTrailing();
    }

    private static void requirePageSize(int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("page size must be at least 1, was " + pageSize);
        }
    }
}
