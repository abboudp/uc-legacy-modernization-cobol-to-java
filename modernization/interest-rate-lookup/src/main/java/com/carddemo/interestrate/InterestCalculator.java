package com.carddemo.interestrate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Monthly formula from 1300-COMPUTE-INTEREST and its WS-TOTAL-INT accumulator. */
public final class InterestCalculator {
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(1200);
    private BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);

    public static BigDecimal monthlyInterest(BigDecimal transactionCategoryBalance, BigDecimal interestRate) {
        Objects.requireNonNull(transactionCategoryBalance, "transactionCategoryBalance");
        Objects.requireNonNull(interestRate, "interestRate");
        return transactionCategoryBalance.multiply(interestRate)
                .divide(MONTHS_PER_YEAR, 2, RoundingMode.DOWN);
    }

    public static boolean shouldComputeInterest(BigDecimal interestRate) {
        return Objects.requireNonNull(interestRate, "interestRate").compareTo(BigDecimal.ZERO) != 0;
    }

    public BigDecimal addMonthlyInterest(BigDecimal transactionCategoryBalance, BigDecimal interestRate) {
        BigDecimal monthly = monthlyInterest(transactionCategoryBalance, interestRate);
        totalInterest = totalInterest.add(monthly).setScale(2);
        return monthly;
    }

    public BigDecimal totalInterest() {
        return totalInterest;
    }

    public void reset() {
        totalInterest = BigDecimal.ZERO.setScale(2);
    }
}
