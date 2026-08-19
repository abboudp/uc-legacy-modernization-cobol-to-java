package com.carddemo.copybook.codec;

public record TransactionTypeRecord(
        String transactionType,
        String transactionTypeDescription,
        String filler) {
}
