package com.abboudp.cardxref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * In-memory, read-only projection of the CARDXREF KSDS and its account AIX.
 */
public final class CardXrefReadModel {
    private static final Comparator<CardXrefRecord> BASE_KEY_ORDER =
            Comparator.comparing(CardXrefRecord::cardNum);

    private final List<CardXrefRecord> records;
    private final Map<String, List<CardXrefRecord>> recordsByAccount;

    public CardXrefReadModel(List<CardXrefRecord> sourceRecords) {
        Objects.requireNonNull(sourceRecords, "sourceRecords must not be null");
        List<CardXrefRecord> sorted = new ArrayList<>(sourceRecords);
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sourceRecords must not contain null records");
        }
        sorted.sort(BASE_KEY_ORDER);
        records = Collections.unmodifiableList(sorted);

        Map<String, List<CardXrefRecord>> byAccount = new LinkedHashMap<>();
        for (CardXrefRecord record : records) {
            byAccount.computeIfAbsent(record.acctId(), ignored -> new ArrayList<>()).add(record);
        }
        byAccount.replaceAll((account, accountRecords) ->
                Collections.unmodifiableList(new ArrayList<>(accountRecords)));
        recordsByAccount = Collections.unmodifiableMap(byAccount);
    }

    public List<CardXrefRecord> records() {
        return records;
    }

    public Optional<CardXrefRecord> findByCardNumber(String cardNumber) {
        return records.stream()
                .filter(record -> record.cardNum().equals(cardNumber))
                .findFirst();
    }

    /**
     * Finds every card on an account, in base KSDS card-key order.
     */
    public List<CardXrefRecord> findByAccountId(String accountId) {
        return recordsByAccount.getOrDefault(accountId, List.of());
    }

    /**
     * Checks that the base-key and nonunique account-index directions agree.
     */
    public boolean verifyBidirectionalConsistency() {
        return consistencyCheck().consistent();
    }

    public ConsistencyResult consistencyCheck() {
        List<String> errors = new ArrayList<>();
        Set<String> accountIds = new LinkedHashSet<>(
                records.stream().map(CardXrefRecord::acctId).collect(Collectors.toSet()));
        for (String accountId : accountIds) {
            Set<String> indexedCards = findByAccountId(accountId).stream()
                    .map(CardXrefRecord::cardNum)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> filteredCards = records.stream()
                    .filter(record -> record.acctId().equals(accountId))
                    .map(CardXrefRecord::cardNum)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!indexedCards.equals(filteredCards)) {
                errors.add("account " + accountId + " index/filter mismatch");
            }
        }
        for (CardXrefRecord record : records) {
            Optional<CardXrefRecord> byCard = findByCardNumber(record.cardNum());
            if (byCard.isEmpty() || !byCard.get().acctId().equals(record.acctId())) {
                errors.add("card " + record.cardNum() + " does not resolve to its account");
            }
        }
        return new ConsistencyResult(errors.isEmpty(), errors);
    }

    public record ConsistencyResult(boolean consistent, List<String> errors) {
        public ConsistencyResult {
            errors = List.copyOf(errors);
        }
    }
}
