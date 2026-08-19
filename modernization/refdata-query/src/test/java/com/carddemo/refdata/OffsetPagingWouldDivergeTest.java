package com.carddemo.refdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Why the keyset behaviour of the COBOL cursors cannot be replaced with {@code LIMIT/OFFSET}.
 *
 * <p>The browse is pseudo-conversational: the cursor is opened and closed inside one CICS
 * interaction (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:1610-1611, :1722-1723) and the only
 * state kept between screens is the key of a row already read
 * ({@code WS-CA-FIRST-TR-CODE}/{@code WS-CA-LAST-TR-CODE}, :397-401). The same screen also deletes
 * and inserts rows ({@code 9300-DELETE-RECORD} at :1900, and {@code COTRTUPC} inserting at
 * COTRTUPC.cbl:1597), so the row set can change between two pages of the same browse. An offset
 * counts rows; a key does not.
 */
class OffsetPagingWouldDivergeTest {

    private static final int PAGE = 3;

    @Test
    void deletingAnEarlierRowMakesOffsetSkipARowWhileKeysetDoesNot() throws SQLException {
        DataSource dataSource = RefDataTestDatabase.seeded();
        TransactionTypeQueryService service = new JdbcTransactionTypeQueryService(dataSource);

        BrowsePage firstPage = service.firstPage(PAGE);
        assertEquals(List.of("01", "02", "03"), firstPage.keys());

        // Somebody deletes a row from the page already displayed, as the delete path of this very
        // screen does (COTRTLIC.cbl:1900-1903).
        delete(dataSource, "02");

        // Keyset: page 2 is derived from the key of the first row of page 2, so it is unaffected.
        assertEquals(List.of("04", "05", "06"), service.nextPage(firstPage).keys());

        // OFFSET 3: the deletion shifted every following row up by one, so "04" is never shown.
        List<String> offsetPage2 = offsetPage(dataSource, PAGE, PAGE);
        assertEquals(List.of("05", "06", "07"), offsetPage2);
        assertNotEquals(service.nextPage(firstPage).keys(), offsetPage2);
    }

    @Test
    void insertingAnEarlierRowMakesOffsetRepeatARowWhileKeysetDoesNot() throws SQLException {
        DataSource dataSource = RefDataTestDatabase.seeded();
        TransactionTypeQueryService service = new JdbcTransactionTypeQueryService(dataSource);

        BrowsePage firstPage = service.firstPage(PAGE);
        insert(dataSource, "00", "Inserted before the browsed page");

        assertEquals(List.of("04", "05", "06"), service.nextPage(firstPage).keys());
        assertEquals(List.of("03", "04", "05"), offsetPage(dataSource, PAGE, PAGE),
                "OFFSET re-displays 03, which the user has already seen");
    }

    private static List<String> offsetPage(DataSource dataSource, int limit, int offset)
            throws SQLException {
        List<String> keys = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT TR_TYPE FROM CARDDEMO.TRANSACTION_TYPE "
                             + "ORDER BY TR_TYPE LIMIT ? OFFSET ?")) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    keys.add(resultSet.getString(1).stripTrailing());
                }
            }
        }
        return keys;
    }

    private static void delete(DataSource dataSource, String trType) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM CARDDEMO.TRANSACTION_TYPE_CATEGORY "
                    + "WHERE TRC_TYPE_CODE = '" + trType + "'");
            statement.executeUpdate(
                    "DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = '" + trType + "'");
        }
    }

    private static void insert(DataSource dataSource, String trType, String description)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO CARDDEMO.TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) "
                             + "VALUES (?, ?)")) {
            statement.setString(1, trType);
            statement.setString(2, description);
            statement.executeUpdate();
        }
    }
}
