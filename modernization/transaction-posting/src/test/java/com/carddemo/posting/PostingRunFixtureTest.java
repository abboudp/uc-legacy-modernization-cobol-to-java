package com.carddemo.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.carddemo.posting.PostingDatasets.InMemoryAccountStore;
import com.carddemo.posting.PostingDatasets.InMemoryCardXrefStore;
import com.carddemo.posting.PostingDatasets.InMemoryTransactionCategoryBalanceStore;
import com.carddemo.posting.PostingDatasets.RecordingRejectStore;
import com.carddemo.posting.PostingDatasets.RecordingTransactionMaster;

/**
 * A whole {@code POSTTRAN} step over the golden-master fixtures: 300 daily transactions against the 50
 * accounts, 50 cross-reference rows and 50 category balances of {@code app/data/ASCII}.
 *
 * <p>The expected numbers below are not transcribed from a run of this code — every one of them is either
 * recomputed from the fixtures inside the test, or derived from the rule the COBOL states.
 */
class PostingRunFixtureTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    private InMemoryAccountStore accounts;
    private InMemoryTransactionCategoryBalanceStore categoryBalances;
    private RecordingTransactionMaster master;
    private RecordingRejectStore rejects;
    private PostingRun.Summary summary;

    @BeforeEach
    void runThePostingStep() {
        accounts = new InMemoryAccountStore(Fixtures.accounts());
        categoryBalances = new InMemoryTransactionCategoryBalanceStore(Fixtures.categoryBalances());
        master = new RecordingTransactionMaster();
        rejects = new RecordingRejectStore();
        PostingEngine engine = new PostingEngine(
                new InMemoryCardXrefStore(Fixtures.cardXrefs()),
                accounts,
                categoryBalances,
                master,
                rejects,
                FIXED);
        summary = new PostingRun(engine).process(Fixtures.dailyTransactions());
    }

    @Test
    void everyDailyTransactionIsEitherPostedOrRejectedExactlyOnce() {
        assertEquals(Fixtures.dailyTransactions().size(), summary.processed());
        assertEquals(summary.processed(), master.written().size() + rejects.written().size());
        assertEquals(summary.rejected(), rejects.written().size());
    }

    @Test
    void theStepEndsWithReturnCode4WhileAnythingIsRejected() {
        assertTrue(summary.rejected() > 0, "the fixture set is expected to exercise the reject path");
        assertEquals(4, summary.returnCode());
        assertEquals(0, new PostingRun.Summary(300, 0).returnCode());
    }

    @Test
    void everyRejectReasonHoldsWhenRecheckedAgainstTheInputDatasets() {
        Map<String, CardXrefRecord> xrefs = new HashMap<>();
        Fixtures.cardXrefs().forEach(line -> {
            CardXrefRecord record = CardXrefRecord.decode(line);
            xrefs.put(record.cardNumber(), record);
        });
        Map<Long, AccountRecord> originalAccounts = new HashMap<>();
        Fixtures.accounts().forEach(line -> {
            AccountRecord record = AccountRecord.decode(line);
            originalAccounts.put(record.accountId(), record);
        });

        for (RejectedTransaction rejected : rejects.written()) {
            DailyTransaction daily = rejected.transaction();
            CardXrefRecord xref = xrefs.get(daily.cardNumber());
            switch (rejected.reason()) {
                case INVALID_CARD_NUMBER -> assertTrue(xref == null,
                        "card " + daily.cardNumber() + " is cross-referenced but was rejected as unknown");
                case ACCOUNT_NOT_FOUND -> assertTrue(xref != null
                                && !originalAccounts.containsKey(xref.accountId()),
                        "account for card " + daily.cardNumber() + " exists but was rejected as missing");
                case ACCOUNT_EXPIRED -> assertTrue(
                        originalAccounts.get(xref.accountId()).expirationDate()
                                .compareTo(daily.originDate()) < 0,
                        "transaction " + daily.id() + " is not past its account expiration date");
                // The limit check reads the cycle buckets as they stand after earlier transactions have
                // posted, so it cannot be rechecked against the input dataset; what is checkable is that the
                // other three reasons did not apply.
                case OVERLIMIT -> assertTrue(xref != null
                                && originalAccounts.get(xref.accountId()).expirationDate()
                                        .compareTo(daily.originDate()) >= 0,
                        "transaction " + daily.id() + " should have been rejected for another reason");
            }
        }
    }

    /**
     * The interesting property of the fixture run: for each account, the change to every monetary field is
     * exactly the sum of the amounts of the transactions that were posted to it, in file order.
     */
    @Test
    void accountBalancesMoveByTheSumOfThePostedAmounts() {
        Map<Long, AccountRecord> before = new HashMap<>();
        Fixtures.accounts().forEach(line -> {
            AccountRecord record = AccountRecord.decode(line);
            before.put(record.accountId(), record);
        });
        Map<String, Long> accountByCard = new HashMap<>();
        Fixtures.cardXrefs().forEach(line -> {
            CardXrefRecord record = CardXrefRecord.decode(line);
            accountByCard.put(record.cardNumber(), record.accountId());
        });

        Map<Long, List<BigDecimal>> postedAmounts = new HashMap<>();
        for (PostedTransaction posted : master.written()) {
            postedAmounts.computeIfAbsent(accountByCard.get(posted.cardNumber()), key -> new ArrayList<>())
                    .add(posted.amount());
        }

        for (Map.Entry<Long, AccountRecord> entry : before.entrySet()) {
            AccountRecord expected = entry.getValue();
            for (BigDecimal amount : postedAmounts.getOrDefault(entry.getKey(), List.of())) {
                expected = expected.applyTransaction(amount);
            }
            assertEquals(expected, accounts.find(entry.getKey()).orElseThrow(),
                    "account " + entry.getKey() + " did not move by the sum of its posted amounts");
        }
    }

    @Test
    void categoryBalancesMoveByTheSumOfThePostedAmountsPerAccountTypeAndCategory() {
        Map<TransactionCategoryKey, BigDecimal> expected = new HashMap<>();
        Fixtures.categoryBalances().forEach(line -> {
            TransactionCategoryBalance record = TransactionCategoryBalance.decode(line);
            expected.put(record.key(), record.balance());
        });
        Map<String, Long> accountByCard = new HashMap<>();
        Fixtures.cardXrefs().forEach(line -> {
            CardXrefRecord record = CardXrefRecord.decode(line);
            accountByCard.put(record.cardNumber(), record.accountId());
        });

        for (PostedTransaction posted : master.written()) {
            TransactionCategoryKey key = new TransactionCategoryKey(
                    accountByCard.get(posted.cardNumber()), posted.typeCode(), posted.categoryCode());
            expected.merge(key, posted.amount(), BigDecimal::add);
        }

        for (Map.Entry<TransactionCategoryKey, BigDecimal> entry : expected.entrySet()) {
            assertEquals(entry.getValue(),
                    categoryBalances.find(entry.getKey()).orElseThrow(
                            () -> new AssertionError("missing TCATBAL row " + entry.getKey().image())).balance(),
                    "TCATBAL row " + entry.getKey().image() + " has the wrong balance");
        }
        assertEquals(expected.size(), categoryBalances.unload().size());
    }

    @Test
    void postedRecordsKeepTheirDailyTransactionImageApartFromTheProcessingTimestamp() {
        Map<String, String> dailyById = new HashMap<>();
        Fixtures.dailyTransactions()
                .forEach(line -> dailyById.put(DailyTransaction.decode(line).id(), line));

        for (PostedTransaction posted : master.written()) {
            String daily = dailyById.get(posted.id());
            String encoded = posted.encode();
            assertEquals(daily.substring(0, 304), encoded.substring(0, 304));
            assertEquals("2022-07-18-00.00.00.000000", encoded.substring(304, 330));
        }
    }

    @Test
    void unloadedDatasetsStayRecordLengthExact() {
        accounts.unload().forEach(record -> assertEquals(AccountRecord.LENGTH, record.length()));
        categoryBalances.unload()
                .forEach(record -> assertEquals(TransactionCategoryBalance.LENGTH, record.length()));
        rejects.written()
                .forEach(record -> assertEquals(RejectedTransaction.LENGTH, record.encode().length()));
        master.written().forEach(record -> assertEquals(PostedTransaction.LENGTH, record.encode().length()));
    }

}
