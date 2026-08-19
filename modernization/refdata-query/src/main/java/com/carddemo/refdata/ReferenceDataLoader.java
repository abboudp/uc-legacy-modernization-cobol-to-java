package com.carddemo.refdata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import java.io.InputStream;

/**
 * Reads the repository's own reference-data fixtures and loads them into a relational database.
 *
 * <p>The fixtures are the 60-byte fixed-length records the mainframe unload produces
 * ({@code TRANEXTR.jcl:76-84} for types, {@code :106-115} for categories), whose layouts are
 * {@code app/cpy/CVTRA03Y.cpy} (type: {@code X(02)}, {@code X(50)}, {@code FILLER X(08)}) and
 * {@code app/cpy/CVTRA04Y.cpy} (category: {@code X(02)}, {@code 9(04)}, {@code X(50)},
 * {@code FILLER X(04)}).
 *
 * <p>The ASCII copies under {@code app/data/ASCII/} are used, so this slice needs no EBCDIC codec.
 * They carry mixed line terminators, so the terminator is stripped rather than assumed.
 */
public final class ReferenceDataLoader {

    /** {@code RECLN = 60} (app/cpy/CVTRA03Y.cpy:2, app/cpy/CVTRA04Y.cpy:2). */
    public static final int RECORD_LENGTH = 60;

    private ReferenceDataLoader() {
    }

    public static List<TransactionType> readTransactionTypes(Path fixture) {
        List<TransactionType> types = new ArrayList<>();
        for (String record : records(fixture)) {
            types.add(new TransactionType(field(record, 0, 2), field(record, 2, 50)));
        }
        return List.copyOf(types);
    }

    public static List<TransactionTypeCategory> readTransactionTypeCategories(Path fixture) {
        List<TransactionTypeCategory> categories = new ArrayList<>();
        for (String record : records(fixture)) {
            categories.add(new TransactionTypeCategory(field(record, 0, 2), field(record, 2, 4),
                    field(record, 6, 50)));
        }
        return List.copyOf(categories);
    }

    /** Creates the schema of {@code schema.sql} in the given database. */
    public static void createSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // Comments are removed before splitting: they document the Db2 evidence and contain
            // punctuation, including semicolons.
            String ddl = resource("/schema.sql").replaceAll("(?m)--.*$", "");
            for (String statementText : ddl.split(";")) {
                if (!statementText.isBlank()) {
                    statement.execute(statementText);
                }
            }
        } catch (SQLException e) {
            throw new RefDataQueryException("schema creation", e);
        }
    }

    /**
     * Inserts the fixture rows. Types are inserted first: the category table's foreign key on
     * {@code TRC_TYPE_CODE} (app/app-transaction-type-db2/ctl/DB2CREAT.ctl:96-99) makes the order
     * mandatory, exactly as it does in the load job's steps 20 and 30
     * (app/app-transaction-type-db2/jcl/CREADB21.jcl:61-79).
     */
    public static void seed(DataSource dataSource, List<TransactionType> types,
                            List<TransactionTypeCategory> categories) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO CARDDEMO.TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (?, ?)")) {
                for (TransactionType type : types) {
                    insert.setString(1, type.trType());
                    insert.setString(2, type.trDescription());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO CARDDEMO.TRANSACTION_TYPE_CATEGORY "
                            + "(TRC_TYPE_CODE, TRC_TYPE_CATEGORY, TRC_CAT_DATA) VALUES (?, ?, ?)")) {
                for (TransactionTypeCategory category : categories) {
                    insert.setString(1, category.trcTypeCode());
                    insert.setString(2, category.trcTypeCategory());
                    insert.setString(3, category.trcCatData());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (SQLException e) {
            throw new RefDataQueryException("reference data seeding", e);
        }
    }

    private static List<String> records(Path fixture) {
        List<String> records = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(fixture, StandardCharsets.US_ASCII)) {
                String record = line.stripTrailing().isEmpty() ? "" : stripTerminator(line);
                if (record.isEmpty()) {
                    continue;
                }
                if (record.length() != RECORD_LENGTH) {
                    throw new IllegalStateException(fixture + " record is " + record.length()
                            + " bytes, expected " + RECORD_LENGTH + ": [" + record + "]");
                }
                records.add(record);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return records;
    }

    private static String stripTerminator(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /** Fixed-width field extraction; trailing blanks are not data (the columns are trimmed). */
    private static String field(String record, int offset, int length) {
        return record.substring(offset, offset + length).stripTrailing();
    }

    private static String resource(String name) {
        try (InputStream in = ReferenceDataLoader.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("resource not on the classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
