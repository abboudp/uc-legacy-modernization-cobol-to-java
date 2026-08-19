package com.carddemo.refdata;

import java.util.List;

/**
 * One displayed browse row: a transaction type plus the categories that hang off it.
 *
 * <p>The COBOL screen row carries only the type code and description — 52 bytes, 7 rows
 * (app/app-transaction-type-db2/cbl/COTRTLIC.cbl:391-392). The categories are attached here
 * because the read model of the slice covers both reference tables; see README
 * "Where the source contradicted the plan".
 */
public record BrowseRow(TransactionType type, List<TransactionTypeCategory> categories) {

    public BrowseRow {
        categories = List.copyOf(categories);
    }

    public String key() {
        return type.trType();
    }
}
