package com.carddemo.interestrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class InterestCalculatorTest {
    @Test
    void truncatesNonDividingProductsTowardZero() {
        assertEquals(new BigDecimal("2.57"),
                InterestCalculator.monthlyInterest(new BigDecimal("1234.56"), new BigDecimal("2.50")));
        assertEquals(new BigDecimal("0.02"),
                InterestCalculator.monthlyInterest(new BigDecimal("10.00"), new BigDecimal("2.50")));
        assertEquals(new BigDecimal("0.01"),
                InterestCalculator.monthlyInterest(new BigDecimal("5.55"), new BigDecimal("2.50")));
    }

    @Test
    void handlesZeroRateAndZeroBalance() {
        assertEquals(new BigDecimal("0.00"),
                InterestCalculator.monthlyInterest(new BigDecimal("1234.56"), new BigDecimal("0.00")));
        assertEquals(new BigDecimal("0.00"),
                InterestCalculator.monthlyInterest(new BigDecimal("0.00"), new BigDecimal("2.50")));
        // CBACT04C.cbl:214 computes interest only when DIS-INT-RATE is not zero.
        assertFalse(InterestCalculator.shouldComputeInterest(new BigDecimal("0.00")));
        assertTrue(InterestCalculator.shouldComputeInterest(new BigDecimal("1.50")));
    }

    @Test
    void SYNTHETICNegativeBalanceTruncatesTowardZero() {
        // TCATBALF supplies the caller balance; it is not read from this fixture.
        assertEquals(new BigDecimal("-2.57"),
                InterestCalculator.monthlyInterest(new BigDecimal("-1234.56"), new BigDecimal("2.50")));
    }

    @Test
    void accumulatorAddsAtScaleTwoAndCanReset() {
        InterestCalculator calculator = new InterestCalculator();
        calculator.addMonthlyInterest(new BigDecimal("1234.56"), new BigDecimal("2.50"));
        calculator.addMonthlyInterest(new BigDecimal("10.00"), new BigDecimal("2.50"));
        assertEquals(new BigDecimal("2.59"), calculator.totalInterest());
        calculator.reset();
        assertEquals(new BigDecimal("0.00"), calculator.totalInterest());
    }
}
