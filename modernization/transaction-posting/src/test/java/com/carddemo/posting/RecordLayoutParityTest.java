package com.carddemo.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Golden-master layout checks: every record of every posting fixture must decode and re-encode to exactly the
 * bytes it came from. This is what makes the rest of the slice trustworthy — a field offset that is one byte
 * out shows up here rather than as a silently wrong balance.
 */
class RecordLayoutParityTest {

    @Test
    void dailyTransactionRecordsRoundTrip() {
        List<String> records = Fixtures.dailyTransactions();
        assertEquals(300, records.size());
        for (String record : records) {
            assertEquals(DailyTransaction.LENGTH, record.length());
            DailyTransaction decoded = DailyTransaction.decode(record);
            assertEquals(record, PostedTransaction.from(decoded, decoded.processedTimestamp()).encode());
        }
    }

    @Test
    void accountRecordsRoundTrip() {
        List<String> records = Fixtures.accounts();
        assertEquals(50, records.size());
        for (String record : records) {
            assertEquals(AccountRecord.LENGTH, record.length());
            assertEquals(record, AccountRecord.decode(record).encode());
        }
    }

    @Test
    void cardXrefRecordsRoundTripOnceTheirOmittedTrailingFillerIsRestored() {
        for (String record : Fixtures.cardXrefs()) {
            assertEquals(CobolFields.pad(record, CardXrefRecord.LENGTH), CardXrefRecord.decode(record).encode());
        }
    }

    @Test
    void categoryBalanceRecordsRoundTrip() {
        for (String record : Fixtures.categoryBalances()) {
            assertEquals(TransactionCategoryBalance.LENGTH, record.length());
            assertEquals(record, TransactionCategoryBalance.decode(record).encode());
        }
    }

    @Test
    void decodesTheFirstDailyTransactionFieldByField() {
        DailyTransaction first = DailyTransaction.decode(Fixtures.dailyTransactions().get(0));

        assertEquals("0000000000683580", first.id());
        assertEquals("01", first.typeCode());
        assertEquals(1, first.categoryCode());
        assertEquals("POS TERM  ", first.source());
        assertEquals("Purchase at Abshire-Lowe", first.description().trim());
        assertEquals(new BigDecimal("504.77"), first.amount());
        assertEquals(800000000L, first.merchantId());
        assertEquals("Abshire-Lowe", first.merchantName().trim());
        assertEquals("North Enoshaven", first.merchantCity().trim());
        assertEquals("72112", first.merchantZip().trim());
        assertEquals("4859452612877065", first.cardNumber());
        assertEquals("2022-06-10 19:27:53.000000", first.originTimestamp());
        assertEquals("2022-06-10", first.originDate());
    }

    @Test
    void decodesTheFirstAccountFieldByField() {
        AccountRecord first = AccountRecord.decode(Fixtures.accounts().get(0));

        assertEquals(1L, first.accountId());
        assertEquals("Y", first.activeStatus());
        assertEquals(new BigDecimal("194.00"), first.currentBalance());
        assertEquals(new BigDecimal("2020.00"), first.creditLimit());
        assertEquals(new BigDecimal("1020.00"), first.cashCreditLimit());
        assertEquals("2014-11-20", first.openDate());
        assertEquals("2025-05-20", first.expirationDate());
        assertEquals(new BigDecimal("0.00"), first.currentCycleCredit());
        assertEquals(new BigDecimal("0.00"), first.currentCycleDebit());
        // Fixture quirk worth knowing before any account slice is written: the account records carry the
        // disclosure group id in ACCT-ADDR-ZIP and leave ACCT-GROUP-ID blank, which is why CBACT04C
        // interest resolution falls back to the DEFAULT group for every fixture account.
        assertEquals("A000000000", first.addressZip());
        assertEquals("          ", first.groupId());
    }
}
