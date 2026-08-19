package com.carddemo.refdata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/**
 * An in-process H2 database seeded from the repository's own ASCII reference-data fixtures.
 *
 * <p>The fixtures are located by walking up from the working directory until the repository root is
 * found, so the module stays buildable from anywhere in a clean checkout.
 */
final class RefDataTestDatabase {

    /**
     * The unload of {@code CARDDEMO.TRANSACTION_TYPE}. The plan documents call this dataset
     * {@code AWS.M2.CARDDEMO.TRANTYPE.PS} (its MVS name at TRANEXTR.jcl:73); in the repository the
     * ASCII copy is checked in under this path.
     */
    static final String TRANTYPE_FIXTURE = "app/data/ASCII/trantype.txt";

    /** The unload of {@code CARDDEMO.TRANSACTION_TYPE_CATEGORY} (TRANEXTR.jcl:103). */
    static final String TRANCATG_FIXTURE = "app/data/ASCII/trancatg.txt";

    private RefDataTestDatabase() {
    }

    static DataSource seeded() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:refdata-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        ReferenceDataLoader.createSchema(dataSource);
        ReferenceDataLoader.seed(dataSource, types(), categories());
        return dataSource;
    }

    /** Same schema, no rows. */
    static DataSource emptyDatabase() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:refdata-empty-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        ReferenceDataLoader.createSchema(dataSource);
        return dataSource;
    }

    static List<TransactionType> types() {
        return ReferenceDataLoader.readTransactionTypes(fixture(TRANTYPE_FIXTURE));
    }

    static List<TransactionTypeCategory> categories() {
        return ReferenceDataLoader.readTransactionTypeCategories(fixture(TRANCATG_FIXTURE));
    }

    static Path fixture(String relativePath) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("fixture not found from " + Path.of("").toAbsolutePath()
                + ": " + relativePath);
    }
}
