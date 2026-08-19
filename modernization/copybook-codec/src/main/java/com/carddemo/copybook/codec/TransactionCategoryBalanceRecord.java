package com.carddemo.copybook.codec;

import java.math.BigDecimal;

public record TransactionCategoryBalanceRecord(
        long accountId,
        String transactionTypeCode,
        int categoryCode,
        BigDecimal balance,
        String filler) {
}
