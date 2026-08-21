package com.carddemo.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.carddemo.posting.PostingDatasets.InMemoryAccountStore;
import com.carddemo.posting.PostingDatasets.InMemoryCardXrefStore;
import com.carddemo.posting.PostingDatasets.InMemoryTransactionCategoryBalanceStore;
import com.carddemo.posting.PostingDatasets.RecordingRejectStore;
import com.carddemo.posting.PostingDatasets.RecordingTransactionMaster;

/** Rule-by-rule checks of the four reject paths and the three updates {@code CBTRN02C} performs. */
class PostingEngineTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2022-07-18T09:15:31.640Z"), ZoneOffset.UTC);
    private static final String CARD = "4859452612877065";

    private final RecordingTransactionMaster master = new RecordingTransactionMaster();
    private final RecordingRejectStore rejects = new RecordingRejectStore();

    private PostingEngine engine(List<String> xrefs, List<String> accounts, List<String> balances) {
        return new PostingEngine(
                new InMemoryCardXrefStore(xrefs),
                new InMemoryAccountStore(accounts),
                new InMemoryTransactionCategoryBalanceStore(balances),
                master,
                rejects,
                FIXED);
    }

    @Test
    void rejectsWithReason100WhenTheCardIsNotCrossReferenced() {
        PostingEngine engine = engine(List.of(), List.of(), List.of());

        PostingEngine.Outcome outcome = post(engine, transaction(CARD, "100.00", "2022-06-10"));

        assertEquals(RejectReason.INVALID_CARD_NUMBER, rejected(outcome).reason());
        assertTrue(master.written().isEmpty());
    }

    @Test
    void rejectsWithReason101WhenTheCrossReferencedAccountIsMissing() {
        PostingEngine engine = engine(List.of(xref(CARD, 77)), List.of(), List.of());

        PostingEngine.Outcome outcome = post(engine, transaction(CARD, "100.00", "2022-06-10"));

        assertEquals(RejectReason.ACCOUNT_NOT_FOUND, rejected(outcome).reason());
    }

    @Test
    void acceptsATransactionThatLandsExactlyOnTheCreditLimit() {
        PostingEngine engine = engine(
                List.of(xref(CARD, 77)),
                List.of(account(77, "1000.00", "300.00", "100.00", "2099-12-31")),
                List.of());

        PostingEngine.Outcome outcome = post(engine, transaction(CARD, "800.00", "2022-06-10"));

        assertInstanceOf(PostingEngine.Outcome.Posted.class, outcome);
    }

    @Test
    void rejectsWithReason102OneCentBeyondTheCreditLimit() {
        PostingEngine engine = engine(
                List.of(xref(CARD, 77)),
                List.of(account(77, "1000.00", "300.00", "100.00", "2099-12-31")),
                List.of());

        PostingEngine.Outcome outcome = post(engine, transaction(CARD, "800.01", "2022-06-10"));

        assertEquals(RejectReason.OVERLIMIT, rejected(outcome).reason());
    }

    @Test
    void comparesTheAvailableLimitAgainstCycleCreditMinusCycleDebitNotTheCurrentBalance() {
        PostingEngine engine = engine(
                List.of(xref(CARD, 77)),
                // A large current balance is irrelevant to the limit check; only the cycle buckets count.
                List.of(account(77, "1000.00", "0.00", "0.00", "2099-12-31", "999999.99")),
                List.of());

        PostingEngine.Outcome outcome = post(engine, transaction(CARD, "1000.00", "2022-06-10"));

        assertInstanceOf(PostingEngine.Outcome.Posted.class, outcome);
    }

    @Test
    void rejectsWithReason103WhenTheTransactionDateIsPastTheExpirationDate() {
        PostingEngine engine = engine(
                List.of(xref(CARD, 77)),
                List.of(account(77, "1000.00", "0.00", "0.00", "2022-06-09")),
                List.of());

        PostingEngine.Outcome outcome = post(engine, transaction(CARD, "1.00", "2022-06-10"));

        assertEquals(RejectReason.ACCOUNT_EXPIRED, rejected(outcome).reason());
    }

    @Test
    void acceptsATransactionOnTheExpirationDateItself() {
        PostingEngine engine = engine(
                List.of(xref(CARD, 77)),
                List.of(account(77, "1000.00", "0.00", "0.00", "2022-06-10")),
                List.of());

        assertInstanceOf(PostingEngine.Outcome.Posted.class, post(engine, transaction(CARD, "1.00", "2022-06-10")));
    }

    @Test
    void reportsExpiryRatherThanOverlimitWhenBothChecksFail() {
        PostingEngine engine = engine(
                List.of(xref(CARD, 77)),
                List.of(account(77, "10.00", "0.00", "0.00", "2022-06-09")),
                List.of());

        PostingEngine.Outcome outcome = post(engine, transaction(CARD, "5000.00", "2022-06-10"));

        assertEquals(RejectReason.ACCOUNT_EXPIRED, rejected(outcome).reason());
    }

    @Test
    void rejectRecordIsTheDailyTransactionImageFollowedByTheValidationTrailer() {
        PostingEngine engine = engine(List.of(), List.of(), List.of());
        DailyTransaction daily = transaction(CARD, "100.00", "2022-06-10");

        String encoded = rejected(post(engine, daily)).encode();

        assertEquals(RejectedTransaction.LENGTH, encoded.length());
        assertEquals(daily.rawRecord(), encoded.substring(0, DailyTransaction.LENGTH));
        assertEquals("0100", encoded.substring(350, 354));
        assertEquals("INVALID CARD NUMBER FOUND", encoded.substring(354).trim());
    }

    @Test
    void postingCreditsTheBalanceAndTheCycleCreditBucket() {
        InMemoryAccountStore accounts =
                new InMemoryAccountStore(List.of(account(77, "1000.00", "300.00", "100.00", "2099-12-31")));
        PostingEngine engine = new PostingEngine(new InMemoryCardXrefStore(List.of(xref(CARD, 77))), accounts,
                new InMemoryTransactionCategoryBalanceStore(List.of()), master, rejects, FIXED);

        post(engine, transaction(CARD, "50.00", "2022-06-10"));

        AccountRecord updated = accounts.find(77).orElseThrow();
        assertEquals(new BigDecimal("350.00"), updated.currentCycleCredit());
        assertEquals(new BigDecimal("100.00"), updated.currentCycleDebit());
        assertEquals(new BigDecimal("50.00"), updated.currentBalance());
    }

    @Test
    void aNegativeAmountAccumulatesIntoTheCycleDebitBucketAsANegativeValue() {
        InMemoryAccountStore accounts =
                new InMemoryAccountStore(List.of(account(77, "1000.00", "300.00", "100.00", "2099-12-31")));
        PostingEngine engine = new PostingEngine(new InMemoryCardXrefStore(List.of(xref(CARD, 77))), accounts,
                new InMemoryTransactionCategoryBalanceStore(List.of()), master, rejects, FIXED);

        post(engine, transaction(CARD, "-40.00", "2022-06-10"));

        AccountRecord updated = accounts.find(77).orElseThrow();
        assertEquals(new BigDecimal("300.00"), updated.currentCycleCredit());
        assertEquals(new BigDecimal("60.00"), updated.currentCycleDebit());
        assertEquals(new BigDecimal("-40.00"), updated.currentBalance());
    }

    @Test
    void createsTheCategoryBalanceRowOnFirstUseAndAccumulatesIntoItAfterwards() {
        InMemoryTransactionCategoryBalanceStore balances =
                new InMemoryTransactionCategoryBalanceStore(List.of());
        PostingEngine engine = new PostingEngine(new InMemoryCardXrefStore(List.of(xref(CARD, 77))),
                new InMemoryAccountStore(List.of(account(77, "100000.00", "0.00", "0.00", "2099-12-31"))),
                balances, master, rejects, FIXED);
        TransactionCategoryKey key = new TransactionCategoryKey(77, "01", 1);

        post(engine, transaction(CARD, "25.50", "2022-06-10"));
        assertEquals(new BigDecimal("25.50"), balances.find(key).orElseThrow().balance());

        post(engine, transaction(CARD, "10.25", "2022-06-10"));
        assertEquals(new BigDecimal("35.75"), balances.find(key).orElseThrow().balance());

        assertEquals(List.of(key.image() + "0000000357E" + " ".repeat(22)), balances.unload());
    }

    @Test
    void stampsTheProcessingTimestampInDb2FormatAndKeepsTheOriginalOne() {
        PostingEngine engine = engine(
                List.of(xref(CARD, 77)),
                List.of(account(77, "1000.00", "0.00", "0.00", "2099-12-31")),
                List.of());

        DailyTransaction daily = transaction(CARD, "1.00", "2022-06-10");
        PostedTransaction posted = ((PostingEngine.Outcome.Posted) post(engine, daily)).transaction();

        assertEquals("2022-07-18-09.15.31.640000", posted.processedTimestamp());
        assertEquals(daily.originTimestamp(), posted.originTimestamp());
        assertEquals(PostedTransaction.LENGTH, posted.encode().length());
    }

    private PostingEngine.Outcome post(PostingEngine engine, DailyTransaction daily) {
        return engine.post(daily);
    }

    private RejectedTransaction rejected(PostingEngine.Outcome outcome) {
        return assertInstanceOf(PostingEngine.Outcome.Rejected.class, outcome).transaction();
    }

    private static DailyTransaction transaction(String cardNumber, String amount, String originDate) {
        String record = CobolFields.pad("0000000000000001", 16)
                + "01"
                + "0001"
                + CobolFields.pad("POS TERM", 10)
                + CobolFields.pad("Test transaction", 100)
                + CobolFields.signed(new BigDecimal(amount), 11, 2)
                + CobolFields.unsigned(9, 9)
                + CobolFields.pad("Merchant", 50)
                + CobolFields.pad("City", 50)
                + CobolFields.pad("72112", 10)
                + CobolFields.pad(cardNumber, 16)
                + CobolFields.pad(originDate + " 19:27:53.000000", 26)
                + " ".repeat(26)
                + " ".repeat(20);
        return DailyTransaction.decode(record);
    }

    private static String xref(String cardNumber, long accountId) {
        return new CardXrefRecord(cardNumber, 5, accountId).encode();
    }

    private static String account(long id, String creditLimit, String cycleCredit, String cycleDebit,
                                  String expiration) {
        return account(id, creditLimit, cycleCredit, cycleDebit, expiration, "0.00");
    }

    private static String account(long id, String creditLimit, String cycleCredit, String cycleDebit,
                                  String expiration, String currentBalance) {
        return new AccountRecord(id, "Y",
                new BigDecimal(currentBalance),
                new BigDecimal(creditLimit),
                new BigDecimal("0.00"),
                "2014-11-20", expiration, expiration,
                new BigDecimal(cycleCredit),
                new BigDecimal(cycleDebit),
                "72112", "A000000000", " ".repeat(178)).encode();
    }
}
