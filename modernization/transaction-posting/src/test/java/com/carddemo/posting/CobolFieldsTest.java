package com.carddemo.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CobolFieldsTest {

    @ParameterizedTest
    @CsvSource({
            "00000001940{, 194.00",
            "0000005047G, 504.77",
            "00000000000{, 0.00",
            "00000000000}, 0.00",
            "0000000504G, 50.47",
            "0000000504P, -50.47",
            "0000005047R, -504.79",
            "00000019400, 194.00",
    })
    void decodesTrailingOverpunchedSigns(String field, String expected) {
        assertEquals(new BigDecimal(expected), CobolFields.signed(field, 0, field.length(), 2));
    }

    @ParameterizedTest
    @CsvSource({
            "194.00, 12, 00000001940{",
            "504.77, 11, 0000005047G",
            "-504.79, 11, 0000005047R",
            "0.00, 11, 0000000000{",
            "-0.01, 11, 0000000000J",
    })
    void encodesTrailingOverpunchedSigns(String value, int length, String expected) {
        assertEquals(expected, CobolFields.signed(new BigDecimal(value), length, 2));
    }

    @Test
    void dropsHighOrderDigitsThatDoNotFitJustAsACobolMoveDoes() {
        // PIC S9(9)V99 holds 11 digits; 1_000_000_000.00 needs 12, so the leading 1 is lost.
        assertEquals("0000000000{", CobolFields.signed(new BigDecimal("1000000000.00"), 11, 2));
        assertEquals("0000000100{", CobolFields.signed(new BigDecimal("1000000010.00"), 11, 2));
    }

    @Test
    void truncatesRatherThanRoundsSubCentAmounts() {
        assertEquals("0000000199I", CobolFields.signed(new BigDecimal("19.999"), 11, 2));
    }

    @Test
    void treatsBlankNumericFieldsAsZero() {
        assertEquals(new BigDecimal("0.00"), CobolFields.signed(" ".repeat(11), 0, 11, 2));
        assertEquals(0L, CobolFields.unsigned(" ".repeat(9), 0, 9));
    }

    @Test
    void rejectsFieldsWhoseSignPositionIsNotADigitOrOverpunch() {
        assertThrows(PostingDataException.class, () -> CobolFields.signed("000000000*", 0, 10, 2));
    }

    @Test
    void readsPastTheEndOfShortRecordsAsSpaces() {
        assertEquals("AB   ", CobolFields.text("AB", 0, 5));
        assertEquals("     ", CobolFields.text("AB", 4, 5));
    }
}
