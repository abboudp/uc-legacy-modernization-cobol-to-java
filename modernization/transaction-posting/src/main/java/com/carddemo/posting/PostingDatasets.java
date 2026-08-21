package com.carddemo.posting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The six datasets of {@code app/jcl/POSTTRAN.jcl} as narrow ports, plus in-memory implementations backed by
 * the sequential fixtures in {@code app/data/ASCII}.
 *
 * <p>Splitting the ports this way keeps {@link PostingRun} free of file-handling: a later slice can back the
 * same interfaces with a relational store without touching the posting rules.
 */
public final class PostingDatasets {

    private PostingDatasets() {
    }

    /** {@code XREFFILE} — read-only random access by card number. */
    public interface CardXrefStore {
        Optional<CardXrefRecord> find(String cardNumber);
    }

    /** {@code ACCTFILE} — opened {@code I-O}: random read followed by rewrite in place. */
    public interface AccountStore {
        Optional<AccountRecord> find(long accountId);

        void rewrite(AccountRecord record);
    }

    /** {@code TCATBALF} — opened {@code I-O}: read, then either write a new row or rewrite the existing one. */
    public interface TransactionCategoryBalanceStore {
        Optional<TransactionCategoryBalance> find(TransactionCategoryKey key);

        void write(TransactionCategoryBalance record);

        void rewrite(TransactionCategoryBalance record);
    }

    /**
     * {@code TRANFILE} — opened {@code OUTPUT}, which on a KSDS discards every record already in the cluster,
     * so a run leaves the transaction master holding only the transactions it posted.
     */
    public interface TransactionMaster {
        void write(PostedTransaction record);
    }

    /** {@code DALYREJS} — sequential output, one 430-byte record per rejected transaction. */
    public interface RejectStore {
        void write(RejectedTransaction record);
    }

    public static final class InMemoryCardXrefStore implements CardXrefStore {

        private final Map<String, CardXrefRecord> records = new LinkedHashMap<>();

        public InMemoryCardXrefStore(List<String> lines) {
            for (String line : lines) {
                CardXrefRecord record = CardXrefRecord.decode(line);
                records.put(record.cardNumber(), record);
            }
        }

        @Override
        public Optional<CardXrefRecord> find(String cardNumber) {
            return Optional.ofNullable(records.get(CobolFields.pad(cardNumber, 16)));
        }
    }

    public static final class InMemoryAccountStore implements AccountStore {

        private final Map<Long, AccountRecord> records = new LinkedHashMap<>();

        public InMemoryAccountStore(List<String> lines) {
            for (String line : lines) {
                AccountRecord record = AccountRecord.decode(line);
                records.put(record.accountId(), record);
            }
        }

        @Override
        public Optional<AccountRecord> find(long accountId) {
            return Optional.ofNullable(records.get(accountId));
        }

        @Override
        public void rewrite(AccountRecord record) {
            if (records.replace(record.accountId(), record) == null) {
                throw new PostingDataException("Rewrite of unknown account " + record.accountId());
            }
        }

        /** The cluster contents in key order, as a sequential unload of the KSDS would produce. */
        public List<String> unload() {
            return records.values().stream()
                    .sorted(Comparator.comparingLong(AccountRecord::accountId))
                    .map(AccountRecord::encode)
                    .toList();
        }
    }

    public static final class InMemoryTransactionCategoryBalanceStore implements TransactionCategoryBalanceStore {

        private final Map<TransactionCategoryKey, TransactionCategoryBalance> records = new LinkedHashMap<>();

        public InMemoryTransactionCategoryBalanceStore(List<String> lines) {
            for (String line : lines) {
                TransactionCategoryBalance record = TransactionCategoryBalance.decode(line);
                records.put(record.key(), record);
            }
        }

        @Override
        public Optional<TransactionCategoryBalance> find(TransactionCategoryKey key) {
            return Optional.ofNullable(records.get(key));
        }

        @Override
        public void write(TransactionCategoryBalance record) {
            if (records.putIfAbsent(record.key(), record) != null) {
                throw new PostingDataException("Duplicate TCATBAL key " + record.key().image());
            }
        }

        @Override
        public void rewrite(TransactionCategoryBalance record) {
            if (records.replace(record.key(), record) == null) {
                throw new PostingDataException("Rewrite of unknown TCATBAL key " + record.key().image());
            }
        }

        public List<String> unload() {
            return records.values().stream()
                    .sorted(Comparator.comparing(record -> record.key().image()))
                    .map(TransactionCategoryBalance::encode)
                    .toList();
        }
    }

    /** Collects the records a run would write, in write order. */
    public static final class RecordingTransactionMaster implements TransactionMaster {

        private final List<PostedTransaction> written = new ArrayList<>();

        @Override
        public void write(PostedTransaction record) {
            written.add(record);
        }

        public List<PostedTransaction> written() {
            return List.copyOf(written);
        }
    }

    public static final class RecordingRejectStore implements RejectStore {

        private final List<RejectedTransaction> written = new ArrayList<>();

        @Override
        public void write(RejectedTransaction record) {
            written.add(record);
        }

        public List<RejectedTransaction> written() {
            return List.copyOf(written);
        }
    }
}
