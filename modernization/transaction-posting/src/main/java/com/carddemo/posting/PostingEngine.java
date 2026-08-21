package com.carddemo.posting;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import com.carddemo.posting.PostingDatasets.AccountStore;
import com.carddemo.posting.PostingDatasets.CardXrefStore;
import com.carddemo.posting.PostingDatasets.RejectStore;
import com.carddemo.posting.PostingDatasets.TransactionCategoryBalanceStore;
import com.carddemo.posting.PostingDatasets.TransactionMaster;

/**
 * The validation and posting rules of {@code app/cbl/CBTRN02C.cbl}, one daily transaction at a time.
 *
 * <p>Deliberate differences from the COBOL, both documented in the module README:
 * <ul>
 *   <li>the legacy program has no transactional boundary other than job success — this class exposes
 *       {@link #post} as the unit of work so a caller can wrap it in one;</li>
 *   <li>{@code 2800-UPDATE-ACCOUNT-REC} sets reason 109 on a failed rewrite but nothing ever inspects it,
 *       so the transaction is written to the master with the account left unchanged. A missing account here
 *       is impossible (the record was just read under the same key), so it raises
 *       {@link PostingDataException} instead of being silently swallowed.</li>
 * </ul>
 */
public final class PostingEngine {

    private final CardXrefStore cardXrefStore;
    private final AccountStore accountStore;
    private final TransactionCategoryBalanceStore categoryBalanceStore;
    private final TransactionMaster transactionMaster;
    private final RejectStore rejectStore;
    private final Clock clock;

    public PostingEngine(CardXrefStore cardXrefStore,
                         AccountStore accountStore,
                         TransactionCategoryBalanceStore categoryBalanceStore,
                         TransactionMaster transactionMaster,
                         RejectStore rejectStore,
                         Clock clock) {
        this.cardXrefStore = cardXrefStore;
        this.accountStore = accountStore;
        this.categoryBalanceStore = categoryBalanceStore;
        this.transactionMaster = transactionMaster;
        this.rejectStore = rejectStore;
        this.clock = clock;
    }

    /** The outcome of posting one daily transaction. */
    public sealed interface Outcome {

        record Posted(PostedTransaction transaction) implements Outcome {
        }

        record Rejected(RejectedTransaction transaction) implements Outcome {
        }
    }

    public Outcome post(DailyTransaction daily) {
        Optional<CardXrefRecord> xref = cardXrefStore.find(daily.cardNumber());
        if (xref.isEmpty()) {
            return reject(daily, RejectReason.INVALID_CARD_NUMBER);
        }
        Optional<AccountRecord> account = accountStore.find(xref.get().accountId());
        if (account.isEmpty()) {
            return reject(daily, RejectReason.ACCOUNT_NOT_FOUND);
        }
        Optional<RejectReason> failure = validateAccount(account.get(), daily);
        if (failure.isPresent()) {
            return reject(daily, failure.get());
        }
        return postValidated(daily, xref.get(), account.get());
    }

    /**
     * Paragraph {@code 1500-B-LOOKUP-ACCT}. Both checks run, so a transaction that is over limit
     * <em>and</em> past expiry is reported as expired: the second {@code MOVE} overwrites the first.
     */
    private Optional<RejectReason> validateAccount(AccountRecord account, DailyTransaction daily) {
        RejectReason reason = null;
        BigDecimal projected = account.currentCycleCredit()
                .subtract(account.currentCycleDebit())
                .add(daily.amount());
        if (account.creditLimit().compareTo(projected) < 0) {
            reason = RejectReason.OVERLIMIT;
        }
        if (account.expirationDate().compareTo(daily.originDate()) < 0) {
            reason = RejectReason.ACCOUNT_EXPIRED;
        }
        return Optional.ofNullable(reason);
    }

    /** Paragraph {@code 2000-POST-TRANSACTION}, in its original TCATBAL / account / master order. */
    private Outcome postValidated(DailyTransaction daily, CardXrefRecord xref, AccountRecord account) {
        PostedTransaction posted = PostedTransaction.from(daily, Db2Timestamp.format(LocalDateTime.now(clock)));
        updateCategoryBalance(daily, xref);
        accountStore.rewrite(account.applyTransaction(daily.amount()));
        transactionMaster.write(posted);
        return new Outcome.Posted(posted);
    }

    /** Paragraph {@code 2700-UPDATE-TCATBAL}: upsert keyed on account, transaction type and category. */
    private void updateCategoryBalance(DailyTransaction daily, CardXrefRecord xref) {
        TransactionCategoryKey key =
                new TransactionCategoryKey(xref.accountId(), daily.typeCode(), daily.categoryCode());
        Optional<TransactionCategoryBalance> existing = categoryBalanceStore.find(key);
        if (existing.isEmpty()) {
            categoryBalanceStore.write(TransactionCategoryBalance.created(key).add(daily.amount()));
        } else {
            categoryBalanceStore.rewrite(existing.get().add(daily.amount()));
        }
    }

    private Outcome reject(DailyTransaction daily, RejectReason reason) {
        RejectedTransaction rejected = new RejectedTransaction(daily, reason);
        rejectStore.write(rejected);
        return new Outcome.Rejected(rejected);
    }
}
