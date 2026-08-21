package com.carddemo.posting;

import java.math.BigDecimal;

/** One {@code ACCOUNT-RECORD} (copybook {@code CVACT01Y}, 300 bytes). */
public record AccountRecord(
        long accountId,
        String activeStatus,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        String openDate,
        String expirationDate,
        String reissueDate,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit,
        String addressZip,
        String groupId,
        String filler) {

    public static final int LENGTH = 300;

    public static AccountRecord decode(String record) {
        String image = CobolFields.pad(record, LENGTH);
        return new AccountRecord(
                CobolFields.unsigned(image, 0, 11),
                CobolFields.text(image, 11, 1),
                CobolFields.signed(image, 12, 12, 2),
                CobolFields.signed(image, 24, 12, 2),
                CobolFields.signed(image, 36, 12, 2),
                CobolFields.text(image, 48, 10),
                CobolFields.text(image, 58, 10),
                CobolFields.text(image, 68, 10),
                CobolFields.signed(image, 78, 12, 2),
                CobolFields.signed(image, 90, 12, 2),
                CobolFields.text(image, 102, 10),
                CobolFields.text(image, 112, 10),
                CobolFields.text(image, 122, 178));
    }

    public String encode() {
        return CobolFields.unsigned(accountId, 11)
                + CobolFields.pad(activeStatus, 1)
                + CobolFields.signed(currentBalance, 12, 2)
                + CobolFields.signed(creditLimit, 12, 2)
                + CobolFields.signed(cashCreditLimit, 12, 2)
                + CobolFields.pad(openDate, 10)
                + CobolFields.pad(expirationDate, 10)
                + CobolFields.pad(reissueDate, 10)
                + CobolFields.signed(currentCycleCredit, 12, 2)
                + CobolFields.signed(currentCycleDebit, 12, 2)
                + CobolFields.pad(addressZip, 10)
                + CobolFields.pad(groupId, 10)
                + CobolFields.pad(filler, 178);
    }

    /**
     * Paragraph {@code 2800-UPDATE-ACCOUNT-REC}: the amount always moves the running balance, and lands in
     * the cycle credit or cycle debit bucket according to its sign. Note that the legacy code adds a
     * negative amount to {@code ACCT-CURR-CYC-DEBIT}, so the debit bucket accumulates negative values.
     */
    public AccountRecord applyTransaction(BigDecimal amount) {
        BigDecimal balance = currentBalance.add(amount);
        BigDecimal credit = currentCycleCredit;
        BigDecimal debit = currentCycleDebit;
        if (amount.signum() >= 0) {
            credit = credit.add(amount);
        } else {
            debit = debit.add(amount);
        }
        return new AccountRecord(accountId, activeStatus, balance, creditLimit, cashCreditLimit, openDate,
                expirationDate, reissueDate, credit, debit, addressZip, groupId, filler);
    }
}
