package com.carddemo.copybook.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CopybookCodecTest {
    private static final Path ROOT = findRepoRoot();
    private static final Path EBCDIC_TRANTYPE =
            ROOT.resolve("app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS");
    private static final Path EBCDIC_TCATBALF =
            ROOT.resolve("app/data/EBCDIC/AWS.M2.CARDDEMO.TCATBALF.PS");
    private static final Path ASCII_TRANTYPE = ROOT.resolve("app/data/ASCII/trantype.txt");
    private static final Path ASCII_TCATBALF = ROOT.resolve("app/data/ASCII/tcatbal.txt");

    @Test
    void transactionTypeFixtureRoundTripsEveryRecord() throws Exception {
        byte[] image = Files.readAllBytes(EBCDIC_TRANTYPE);
        List<byte[]> records = FixedLengthRecords.split(image, TransactionTypeCodec.RECORD_LENGTH);

        assertEquals(7, image.length / TransactionTypeCodec.RECORD_LENGTH);
        assertEquals(7, records.size());
        byte[] rebuilt = records.stream()
                .map(record -> TransactionTypeCodec.encode(TransactionTypeCodec.decode(record, 0)))
                .flatMapToInt(record -> java.util.stream.IntStream.range(0, record.length)
                        .map(index -> Byte.toUnsignedInt(record[index])))
                .collect(() -> new java.io.ByteArrayOutputStream(), (out, value) -> out.write(value),
                        (left, right) -> {
                            try {
                                right.writeTo(left);
                            } catch (java.io.IOException exception) {
                                throw new AssertionError(exception);
                            }
                        }).toByteArray();
        assertArrayEquals(image, rebuilt);
    }

    @Test
    void transactionCategoryBalanceFixtureRoundTripsEveryRecord() throws Exception {
        byte[] image = Files.readAllBytes(EBCDIC_TCATBALF);
        List<byte[]> records = FixedLengthRecords.split(image, TransactionCategoryBalanceCodec.RECORD_LENGTH);

        assertEquals(50, image.length / TransactionCategoryBalanceCodec.RECORD_LENGTH);
        assertEquals(50, records.size());
        byte[] rebuilt = new byte[image.length];
        for (int i = 0; i < records.size(); i++) {
            byte[] encoded = TransactionCategoryBalanceCodec.encode(
                    TransactionCategoryBalanceCodec.decode(records.get(i), 0));
            System.arraycopy(encoded, 0, rebuilt, i * TransactionCategoryBalanceCodec.RECORD_LENGTH,
                    encoded.length);
        }
        assertArrayEquals(image, rebuilt);
        assertEquals((byte) 0xC0, image[27]);
    }

    @Test
    void transactionTypeFieldsMatchAsciiFixture() throws Exception {
        List<String> asciiLines = lines(ASCII_TRANTYPE);
        List<byte[]> records = FixedLengthRecords.read(EBCDIC_TRANTYPE, 60);
        String[] descriptions = {"Purchase", "Payment", "Credit", "Authorization",
                "Refund", "Reversal", "Adjustment"};

        assertEquals(7, asciiLines.size());
        for (int i = 0; i < records.size(); i++) {
            TransactionTypeRecord decoded = TransactionTypeCodec.decode(records.get(i), 0);
            String line = asciiLines.get(i);
            assertEquals(line.substring(0, 2), decoded.transactionType());
            assertEquals(line.substring(2, 52).stripTrailing(), decoded.transactionTypeDescription());
            assertEquals(descriptions[i], decoded.transactionTypeDescription());
            assertEquals("00000000", decoded.filler());
        }
    }

    @Test
    void transactionCategoryFieldsMatchAsciiFixtureAndExposeMangledOverpunch() throws Exception {
        List<String> asciiLines = lines(ASCII_TCATBALF);
        List<byte[]> records = FixedLengthRecords.read(EBCDIC_TCATBALF, 50);

        assertEquals(50, asciiLines.size());
        for (int i = 0; i < records.size(); i++) {
            TransactionCategoryBalanceRecord decoded =
                    TransactionCategoryBalanceCodec.decode(records.get(i), 0);
            String line = asciiLines.get(i);
            assertEquals(line.substring(0, 11), String.format("%011d", decoded.accountId()));
            assertEquals(line.substring(11, 13), decoded.transactionTypeCode());
            assertEquals(line.substring(13, 17), String.format("%04d", decoded.categoryCode()));
            assertEquals("0000000000{", line.substring(17, 28));
            assertEquals("00000000000", String.format("%011d", decoded.balance().movePointRight(2).longValue()));
            assertEquals("0000000000000000000000", decoded.filler());
        }
    }

    @Test
    void decodedValuesIncludeRealRecordsAndSyntheticSignedValues() {
        TransactionTypeRecord firstType = TransactionTypeCodec.decode(
                FixedLengthRecords.read(EBCDIC_TRANTYPE, 60).get(0), 0);
        assertEquals("01", firstType.transactionType());
        assertEquals("Purchase", firstType.transactionTypeDescription());

        TransactionCategoryBalanceRecord firstBalance = TransactionCategoryBalanceCodec.decode(
                FixedLengthRecords.read(EBCDIC_TCATBALF, 50).get(0), 0);
        assertEquals(1, firstBalance.accountId());
        assertEquals("01", firstBalance.transactionTypeCode());
        assertEquals(1, firstBalance.categoryCode());
        assertEquals(new BigDecimal("0.00"), firstBalance.balance());
        assertEquals(2, firstBalance.balance().scale());

        TransactionCategoryBalanceRecord negative = new TransactionCategoryBalanceRecord(
                42, "01", 1, new BigDecimal("-12345.67"), "synthetic");
        byte[] negativeBytes = TransactionCategoryBalanceCodec.encode(negative);
        assertEquals((byte) 0xD7, negativeBytes[27]);
        assertEquals(negative, TransactionCategoryBalanceCodec.decode(negativeBytes, 0));
        assertArrayEquals(negativeBytes,
                TransactionCategoryBalanceCodec.encode(TransactionCategoryBalanceCodec.decode(negativeBytes, 0)));

        TransactionCategoryBalanceRecord positive = new TransactionCategoryBalanceRecord(
                43, "01", 1, new BigDecimal("42.10"), "synthetic");
        byte[] positiveBytes = TransactionCategoryBalanceCodec.encode(positive);
        assertEquals((byte) 0xC0, positiveBytes[27]);
        assertEquals(positive, TransactionCategoryBalanceCodec.decode(positiveBytes, 0));
        assertArrayEquals(positiveBytes,
                TransactionCategoryBalanceCodec.encode(TransactionCategoryBalanceCodec.decode(positiveBytes, 0)));
    }

    @Test
    void invalidFileLengthIsRejectedWithoutTruncation() {
        CopybookCodecException exception = assertThrows(CopybookCodecException.class,
                () -> FixedLengthRecords.split(new byte[61], 60));
        assertTrue(exception.getMessage().contains("expected a multiple of 60"));
        assertTrue(exception.getMessage().contains("actual 61"));
    }

    @Test
    void invalidZonedDecimalByteIsRejected() {
        byte[] record = new byte[50];
        record[0] = (byte) 0xC1;
        CopybookCodecException exception = assertThrows(CopybookCodecException.class,
                () -> TransactionCategoryBalanceCodec.decode(record, 0));
        assertTrue(exception.getMessage().contains("Invalid zoned-decimal byte 0xC1"));
        assertTrue(exception.getMessage().contains("expected F0-F9"));
    }

    private static List<String> lines(Path path) throws Exception {
        try (Stream<String> stream = Files.lines(path, StandardCharsets.US_ASCII)) {
            return stream.filter(line -> !line.isEmpty()).toList();
        }
    }

    private static Path findRepoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("app/data"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to find repository root containing app/data from "
                + System.getProperty("user.dir"));
    }
}
