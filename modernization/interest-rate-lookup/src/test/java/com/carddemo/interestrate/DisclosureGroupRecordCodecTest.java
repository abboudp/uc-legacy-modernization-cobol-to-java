package com.carddemo.interestrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class DisclosureGroupRecordCodecTest {
    @Test
    void wholeFixtureRoundTripsByteExactly() throws Exception {
        byte[] fixture = FixtureSupport.read(FixtureSupport.fixture());
        List<DisclosureGroupRecord> records = DisclosureGroupRecordCodec.decodeAll(fixture);
        assertEquals(2550, fixture.length);
        byte[] roundTrip = records.stream()
                .map(DisclosureGroupRecord::toBytes)
                .reduce(new byte[0], DisclosureGroupRecordCodecTest::concat);
        assertEquals(51, records.size());
        assertEquals(fixture.length, roundTrip.length);
        assertTrue(java.util.Arrays.equals(fixture, roundTrip));
    }

    @Test
    void fixtureHasPinnedCountsAndFieldSets() throws Exception {
        List<DisclosureGroupRecord> records = DisclosureGroupRecordCodec.decodeAll(
                FixtureSupport.read(FixtureSupport.fixture()));
        assertEquals(51, records.size());
        assertEquals(17, records.stream().filter(r -> r.disAcctGroupId().equals("DEFAULT   ")).count());
        assertEquals(Set.of("A000000000", "DEFAULT   ", "ZEROAPR   "),
                records.stream().map(DisclosureGroupRecord::disAcctGroupId).collect(Collectors.toSet()));
        assertEquals(Set.of("01", "02", "03", "04", "05", "06", "07"),
                records.stream().map(DisclosureGroupRecord::disTranTypeCd).collect(Collectors.toSet()));
        assertEquals(Set.of("0001", "0002", "0003", "0004"),
                records.stream().map(DisclosureGroupRecord::disTranCatCd).collect(Collectors.toSet()));
        assertTrue(records.stream().allMatch(r -> r.disIntRate().scale() == 2));
        assertTrue(records.stream().allMatch(r -> Set.of(
                new BigDecimal("0.00"), new BigDecimal("1.50"), new BigDecimal("2.50"))
                .contains(r.disIntRate())));
    }

    @Test
    void fixtureRatesAndFillersHaveExpectedSignBytes() throws Exception {
        byte[] fixture = FixtureSupport.read(FixtureSupport.fixture());
        List<DisclosureGroupRecord> records = DisclosureGroupRecordCodec.decodeAll(fixture);
        assertEquals(new BigDecimal("1.50"), records.get(0).disIntRate());
        assertEquals("A000000000", records.get(0).disAcctGroupId());
        assertEquals("01", records.get(0).disTranTypeCd());
        assertEquals("0001", records.get(0).disTranCatCd());
        for (int index = 0; index < records.size(); index++) {
            assertEquals((byte) 0xC0, fixture[index * 50 + 21]);
            assertTrue(java.util.Arrays.stream(toUnsigned(records.get(index).filler()))
                    .allMatch(value -> value == 0xF0));
        }
        assertEquals(new BigDecimal("2.50"), find(records, "A000000000", "01", "0002").disIntRate());
        assertEquals(new BigDecimal("0.00"), find(records, "DEFAULT   ", "02", "0001").disIntRate());
        assertEquals(new BigDecimal("0.00"), find(records, "ZEROAPR   ", "07", "0001").disIntRate());
    }

    @Test
    void SYNTHETICNegativeOverpunchRoundTrips() {
        byte[] encoded = SignedZonedDecimalCodec.encode(new BigDecimal("-12.34"));
        assertEquals((byte) 0xD4, encoded[5]);
        assertEquals(new BigDecimal("-12.34"), SignedZonedDecimalCodec.decode(encoded, 0));
    }

    @Test
    void asciiOverpunchRenderingDoesNotParse() throws Exception {
        String firstLine = FilesForTest.readFirstLine(FixtureSupport.asciiFixture());
        assertTrue(firstLine.contains("0150{"));
        byte[] asciiRate = firstLine.substring(16, 22).getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> SignedZonedDecimalCodec.decode(asciiRate, 0));
    }

    @Test
    void rejectsNonMultipleRecordLength() {
        assertThrows(IllegalArgumentException.class, () -> DisclosureGroupRecordCodec.decodeAll(new byte[49]));
    }

    @Test
    void rejectsInvalidOverpunchWithByteAndOffset() {
        byte[] rate = {(byte) 0xF0, (byte) 0xF0, (byte) 0xF1, (byte) 0xF5, (byte) 0xF0, (byte) 0xFF};
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> SignedZonedDecimalCodec.decode(rate, 0));
        assertTrue(exception.getMessage().contains("0xFF"));
        assertTrue(exception.getMessage().contains("offset 5"));
    }

    private static DisclosureGroupRecord find(
            List<DisclosureGroupRecord> records, String group, String type, String category) {
        return records.stream()
                .filter(r -> r.key().equals(new DisclosureGroupKey(group, type, category)))
                .findFirst()
                .orElseThrow();
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static int[] toUnsigned(byte[] bytes) {
        int[] values = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            values[i] = Byte.toUnsignedInt(bytes[i]);
        }
        return values;
    }
}
