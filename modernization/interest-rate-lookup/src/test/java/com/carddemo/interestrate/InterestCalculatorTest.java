package com.carddemo.interestrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class InterestCalculatorTest {
    @Test
    void truncatesNonDividingProductsTowardZero() {
        assertEquals(new BigDecimal("15.43"),
                InterestCalculator.monthlyInterest(new BigDecimal("1234.56"), new BigDecimal("15.00")));
        // 10.00 * 15.00 / 1200 = 0.1250; DOWN gives 0.12, unlike HALF_UP's 0.13.
        assertEquals(new BigDecimal("0.12"),
                InterestCalculator.monthlyInterest(new BigDecimal("10.00"), new BigDecimal("15.00")));
        assertEquals(new BigDecimal("0.11"),
                InterestCalculator.monthlyInterest(new BigDecimal("5.55"), new BigDecimal("25.00")));
    }

    @Test
    void handlesZeroRateAndZeroBalance() {
        assertEquals(new BigDecimal("0.00"),
                InterestCalculator.monthlyInterest(new BigDecimal("1234.56"), new BigDecimal("0.00")));
        assertEquals(new BigDecimal("0.00"),
                InterestCalculator.monthlyInterest(new BigDecimal("0.00"), new BigDecimal("15.00")));
        // CBACT04C.cbl:214 computes interest only when DIS-INT-RATE is not zero.
        assertFalse(InterestCalculator.shouldComputeInterest(new BigDecimal("0.00")));
        assertTrue(InterestCalculator.shouldComputeInterest(new BigDecimal("15.00")));
    }

    @Test
    void SYNTHETICNegativeBalanceTruncatesTowardZero() {
        // TCATBALF supplies the caller balance; it is not read from this fixture.
        assertEquals(new BigDecimal("-15.43"),
                InterestCalculator.monthlyInterest(new BigDecimal("-1234.56"), new BigDecimal("15.00")));
    }

    @Test
    void accumulatorAddsAtScaleTwoAndCanReset() {
        InterestCalculator calculator = new InterestCalculator();
        calculator.addMonthlyInterest(new BigDecimal("1234.56"), new BigDecimal("15.00"));
        calculator.addMonthlyInterest(new BigDecimal("10.00"), new BigDecimal("15.00"));
        assertEquals(new BigDecimal("15.55"), calculator.totalInterest());
        calculator.reset();
        assertEquals(new BigDecimal("0.00"), calculator.totalInterest());
    }
}
