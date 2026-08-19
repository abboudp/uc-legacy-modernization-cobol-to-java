package com.abboudp.cardxref;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CardXrefReadModelTest {
    private static final Path FIXTURE = Path.of(
            System.getProperty(
                    "cardxref.fixture",
                    "../../app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS"));

    @Test
    void wholeFixtureRoundTripsByteForByte() throws IOException {
        byte[] original = Files.readAllBytes(FIXTURE);
        CardXrefFile file = CardXrefFile.load(FIXTURE);
        byte[] roundTrip = file.records().stream()
                .flatMap(record -> Arrays.stream(new byte[][] {record.encode()}))
                .collect(() -> new java.io.ByteArrayOutputStream(),
                        (output, bytes) -> output.writeBytes(bytes),
                        (left, right) -> outputCombine(left, right))
                .toByteArray();

        assertArrayEquals(original, roundTrip);
        assertEquals(50, file.records().size());
    }

    @Test
    void fixtureHasPinnedDistinctCounts() throws IOException {
        List<CardXrefRecord> records = CardXrefFile.load(FIXTURE).records();
        assertEquals(50, records.size());
        assertEquals(50, records.stream().map(CardXrefRecord::acctId).collect(Collectors.toSet()).size());
        assertEquals(50, records.stream().map(CardXrefRecord::custId).collect(Collectors.toSet()).size());
        assertEquals(50, records.stream().map(CardXrefRecord::cardNum).collect(Collectors.toSet()).size());
    }

    @Test
    void fieldOffsetsMatchJclAndCopybook() throws IOException {
        byte[] raw = Files.readAllBytes(FIXTURE);
        CardXrefRecord first = CardXrefFile.load(FIXTURE).records().get(0);
        assertArrayEquals(Arrays.copyOfRange(raw, 0, 16),
                first.cardNum().getBytes(CardXrefRecord.EBCDIC_CHARSET));
        assertArrayEquals(Arrays.copyOfRange(raw, 25, 36),
                first.acctId().getBytes(CardXrefRecord.EBCDIC_CHARSET));
    }

    @Test
    void knownCardAndAccountLookupsWork() throws IOException {
        CardXrefReadModel model = CardXrefFile.load(FIXTURE).readModel();
        CardXrefRecord first = model.findByCardNumber("0500024453765740").orElseThrow();
        assertEquals("000000050", first.custId());
        assertEquals("00000000050", first.acctId());
        assertTrue(model.findByCardNumber("9999999999999999").isEmpty());
        assertTrue(model.findByAccountId("99999999999").isEmpty());
        assertEquals(List.of(first), model.findByAccountId(first.acctId()));
    }

    @Test
    void syntheticMultiCardAccountReturnsBaseKeyOrder() {
        CardXrefRecord later = new CardXrefRecord(
                "0500024453765741", "000000005", "00000000050", new byte[14]);
        CardXrefRecord earlier = new CardXrefRecord(
                "0500024453765739", "000000005", "00000000050", new byte[14]);
        CardXrefReadModel model = new CardXrefReadModel(List.of(later, earlier));

        assertEquals(List.of(earlier, later), model.findByAccountId("00000000050"));
    }

    @Test
    void rejectsTruncatedFile(@TempDir Path tempDir) throws IOException {
        Path truncated = tempDir.resolve("truncated");
        Files.write(truncated, new byte[CardXrefRecord.RECORD_LENGTH - 1]);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> CardXrefFile.load(truncated));
        assertTrue(exception.getMessage().contains("multiple of 50"));
    }

    @Test
    void rejectsNonDigitNumericField() throws IOException {
        byte[] raw = Files.readAllBytes(FIXTURE);
        raw[25] = (byte) 0xC1;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> CardXrefRecord.decode(
                        Arrays.copyOfRange(raw, 0, CardXrefRecord.RECORD_LENGTH)));
        assertTrue(exception.getMessage().contains("account id"));
        assertTrue(exception.getMessage().contains("non-EBCDIC-digit"));
    }

    @Test
    void modelIterationIsAscendingAndMatchesSortedFixture() throws IOException {
        List<CardXrefRecord> fixtureOrder = CardXrefFile.load(FIXTURE).records();
        List<CardXrefRecord> reversed = new java.util.ArrayList<>(fixtureOrder);
        java.util.Collections.reverse(reversed);
        CardXrefReadModel model = new CardXrefReadModel(reversed);

        assertEquals(fixtureOrder, model.records());
        assertTrue(model.records().stream()
                .map(CardXrefRecord::cardNum)
                .collect(Collectors.toList())
                .equals(model.records().stream()
                        .map(CardXrefRecord::cardNum)
                        .sorted()
                        .collect(Collectors.toList())));
    }

    @Test
    void sequentialReaderReadsAllRecordsThenReturnsCobolEof() throws IOException {
        CardXrefSequentialReader reader = new CardXrefSequentialReader(FIXTURE);
        CardXrefSequentialReader.FileOutcome opened = reader.open();
        assertEquals("00", opened.status());
        int count = 0;
        CardXrefSequentialReader.FileOutcome outcome;
        do {
            outcome = reader.readNext();
            if (outcome.record() != null) {
                count++;
            }
        } while (!outcome.isEof());

        assertEquals(50, count);
        assertEquals("10", outcome.status());
        assertFalse(outcome.abended());
        assertEquals("00", reader.close().status());
    }

    @Test
    void missingSequentialFileAbendsWithoutExiting(@TempDir Path tempDir) {
        CardXrefSequentialReader reader =
                new CardXrefSequentialReader(tempDir.resolve("does-not-exist"));

        CardXrefSequentialReader.FileOutcome outcome = reader.open();

        assertEquals("35", outcome.status());
        assertTrue(outcome.abended());
        assertEquals(999, outcome.abendCode());
        assertNotNull(outcome.message());
        assertTrue(outcome.message().contains("FILE STATUS IS: 0035"));
    }

    @Test
    void bidirectionalConsistencyHoldsForFixture() throws IOException {
        CardXrefReadModel.ConsistencyResult result =
                CardXrefFile.load(FIXTURE).readModel().consistencyCheck();

        assertTrue(result.consistent(), result.errors().toString());
        assertTrue(new CardXrefReadModel(CardXrefFile.load(FIXTURE).records())
                .verifyBidirectionalConsistency());
    }

    private static java.io.ByteArrayOutputStream outputCombine(
            java.io.ByteArrayOutputStream left, java.io.ByteArrayOutputStream right) {
        left.writeBytes(right.toByteArray());
        return left;
    }
}
