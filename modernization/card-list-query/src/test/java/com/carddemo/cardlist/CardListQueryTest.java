package com.carddemo.cardlist;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CardListQueryTest {
    private static final Path EBCDIC_FIXTURE = Path.of("../../app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS");
    private static final Path ASCII_FIXTURE = Path.of("../../app/data/ASCII/carddata.txt");
    private static final CardRecordCodec CODEC = new CardRecordCodec();

    @Test
    void wholeFixtureRoundTripIsByteIdentical() throws Exception {
        byte[] source = fixtureBytes();
        byte[] roundTrip = new byte[source.length];
        for (int offset = 0; offset < source.length; offset += 150) {
            System.arraycopy(CODEC.encode(CODEC.decode(Arrays.copyOfRange(source, offset, offset + 150))),
                    0, roundTrip, offset, 150);
        }
        assertArrayEquals(source, roundTrip);
    }

    @Test
    void fixturePinnedCountsAndFillerAreAuthoritative() throws Exception {
        List<CardRecord> records = fixtureRecords();
        assertEquals(50, records.size());
        assertEquals(50, records.stream().map(CardRecord::cardNumber).distinct().count());
        assertEquals(50, records.stream().map(CardRecord::cardAcctId).distinct().count());
        assertEquals(50, records.stream().filter(r -> r.cardActiveStatus().equals("Y")).count());
        assertEquals(0, records.stream().filter(r -> !r.cardActiveStatus().equals("Y")).count());
        assertEquals(records.stream().map(CardRecord::cardNumber).sorted().toList(),
                records.stream().map(CardRecord::cardNumber).toList());
        assertEquals("0500024453765740", records.get(0).cardNumber());
        assertEquals("00000000050", records.get(0).cardAcctId());
        assertEquals("747", records.get(0).cardCvvCd());
        assertTrue(records.stream().allMatch(r -> IntStream.range(0, 59)
                .allMatch(i -> (r.filler()[i] & 0xff) == 0x40)));
    }

    /**
     * CARDFILE.jcl:54 and :85 corroborate CARD-NUM at 0 and CARD-ACCT-ID at 16.
     */
    @Test
    void copybookAndJclOffsetsMatchDecodedFields() throws Exception {
        byte[] first = Arrays.copyOf(fixtureBytes(), 150);
        CardRecord record = CODEC.decode(first);
        assertEquals(record.cardNumber(), new String(first, 0, 16, java.nio.charset.Charset.forName("IBM037")));
        assertEquals(record.cardAcctId(), new String(first, 16, 11, java.nio.charset.Charset.forName("IBM037")));
        assertArrayEquals(record.cardNumber().getBytes(java.nio.charset.Charset.forName("IBM037")),
                Arrays.copyOfRange(first, 0, 16));
        assertArrayEquals(record.cardAcctId().getBytes(java.nio.charset.Charset.forName("IBM037")),
                Arrays.copyOfRange(first, 16, 27));
    }

    /**
     * The ASCII file is a character-field cross-check only, never a signed-field parity source.
     */
    @Test
    void asciiFirstLineCrossChecksCharacterFieldsOnly() throws Exception {
        CardRecord first = fixtureRecords().get(0);
        String ascii = Files.readAllLines(ASCII_FIXTURE, StandardCharsets.US_ASCII).get(0);
        String characterFields = first.cardNumber() + first.cardAcctId() + first.cardCvvCd()
                + first.embossedName() + first.cardExpirationDate() + first.cardActiveStatus();
        assertEquals(characterFields, ascii.substring(0, 91));
    }

    @Test
    void forwardTraversalPreservesSevenSlotsAndFinalShortPage() throws Exception {
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        List<CardListPage> pages = new ArrayList<>();
        String anchor = "";
        for (int screen = 0; ; screen++) {
            CardListPage page = pager.readForward(anchor, CardListFilters.none(), screen);
            pages.add(page);
            if (!page.nextPageExists()) {
                break;
            }
            anchor = page.lastAnchor().cardNumber();
        }
        assertEquals(8, pages.size());
        assertEquals(50, 7 * 7 + 1);
        assertEquals(1, pages.get(7).rows().stream().filter(row -> row != null).count());
        assertEquals(6, pages.get(7).rows().stream().filter(row -> row == null).count());
        for (int i = 0; i < pages.size(); i++) {
            assertEquals(i < 7, pages.get(i).nextPageExists());
        }
        List<String> actual = pages.stream().flatMap(p -> p.rows().stream())
                .filter(row -> row != null).map(CardListRow::cardNumber).toList();
        assertEquals(fixtureRecords().stream().map(CardRecord::cardNumber).toList(), actual);
    }

    @Test
    void backwardTraversalReproducesForwardPartitionInReverse() throws Exception {
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        List<CardListPage> forward = forwardPages(pager);
        for (int i = forward.size() - 1; i > 0; i--) {
            CardListPage backwards = pager.readBackwards(forward.get(i).firstAnchor().cardNumber(),
                    CardListFilters.none(), i + 1);
            assertEquals(forward.get(i - 1).rows(), backwards.rows());
        }
    }

    @Test
    void forwardThenBackwardReturnsFirstPageExactly() throws Exception {
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        CardListPage first = pager.readForward("", CardListFilters.none(), 0);
        CardListPage second = pager.readForward(first.lastAnchor().cardNumber(), CardListFilters.none(), 1);
        CardListPage roundTrip = pager.readBackwards(second.firstAnchor().cardNumber(),
                CardListFilters.none(), 2);
        assertEquals(first.rows(), roundTrip.rows());
        assertEquals(first.firstAnchor(), roundTrip.firstAnchor());
    }

    /**
     * COCRDLIC.cbl:1210-1213 stores the first record of the next page as LAST.
     */
    @Test
    void fullPageLastAnchorIsFirstRecordOfNextPage() throws Exception {
        List<CardRecord> records = fixtureRecords();
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        CardListPage page = pager.readForward("", CardListFilters.none(), 0);
        assertEquals(records.get(7).cardNumber(), page.lastAnchor().cardNumber());
    }

    /**
     * COCRDLIC.cbl:1235-1237 retains the excluded record in the CICS record area.
     */
    @Test
    void shortPageLastAnchorRetainsLastExcludedRecord() {
        List<CardRecord> records = List.of(record("0000000000000001", "00000000001", "Y"),
                record("0000000000000002", "00000000002", "Y"));
        CardListPager pager = new CardListPager(new CardMaster(records));
        CardListFilters filter = new CardListFilters(CardListInputEditor.FilterFlag.ISVALID,
                "00000000001", CardListInputEditor.FilterFlag.BLANK, "0".repeat(16));
        CardListPage page = pager.readForward("", filter, 1);
        assertEquals("0000000000000002", page.lastAnchor().cardNumber());
        assertEquals(1, page.rows().stream().filter(row -> row != null).count());
    }

    /**
     * COCRDLIC.cbl:1216-1219 and :121-122 overwrite NO MORE with NO RECORDS.
     */
    @Test
    void noMatchOverwritesNoMoreWithNoRecordsMessage() throws Exception {
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        CardListFilters filter = new CardListFilters(CardListInputEditor.FilterFlag.ISVALID,
                "99999999999", CardListInputEditor.FilterFlag.BLANK, "0".repeat(16));
        CardListPage page = pager.readForward("", filter, 1);
        assertTrue(page.noRecordsFound());
        assertEquals("NO RECORDS FOUND FOR THIS SEARCH CONDITION.", page.errorMessage());
        assertTrue(page.rows().stream().allMatch(row -> row == null));
        assertFalse(page.nextPageExists());
    }

    /**
     * COCRDLIC.cbl:1361-1368 reports file error when the backward loop reaches file start.
     */
    @Test
    void backwardOffStartReportsGenericFileError() throws Exception {
        CardRecord first = fixtureRecords().get(0);
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        CardListPage page = pager.readBackwards(first.cardNumber(), CardListFilters.none(), 2);
        assertEquals("File Error:READ on CARDFILE", page.errorMessage());
        assertEquals(first.cardNumber(), page.firstAnchor().cardNumber());
        assertTrue(page.rows().stream().allMatch(row -> row == null));
    }

    /**
     * COCRDLIC.cbl:1291-1307 discards the anchor READPREV before filling rows.
     */
    @Test
    void backwardDiscardsFirstReadPrev() {
        List<CardRecord> records = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> record(String.format("%016d", i), String.format("%011d", i), "Y")).toList();
        CardListPager pager = new CardListPager(new CardMaster(records));
        CardListPage page = pager.readBackwards("0000000000000008", CardListFilters.none(), 2);
        assertEquals(7, page.rows().stream().filter(row -> row != null).count());
        assertFalse(page.rows().stream().filter(row -> row != null)
                .anyMatch(row -> row.cardNumber().equals("0000000000000008")));
    }

    /**
     * COCRDLIC.cbl:1177-1178 increments the screen number only when it starts at zero.
     */
    @Test
    void screenNumberIncrementsOnlyFromZero() throws Exception {
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        assertEquals(1, pager.readForward("", CardListFilters.none(), 0).screenNum());
        assertEquals(4, pager.readForward("", CardListFilters.none(), 4).screenNum());
    }

    @Test
    void accountFilterAndCardFilterAndCombinationApplyDuringFill() throws Exception {
        List<CardRecord> records = fixtureRecords();
        CardRecord chosen = records.get(0);
        CardListPager pager = new CardListPager(new CardMaster(fixtureBytes()));
        CardListFilters account = new CardListFilters(CardListInputEditor.FilterFlag.ISVALID,
                chosen.cardAcctId(), CardListInputEditor.FilterFlag.BLANK, "0".repeat(16));
        CardListPage accountPage = pager.readForward("", account, 1);
        assertEquals(1, accountPage.rows().stream().filter(row -> row != null).count());
        assertEquals(chosen.cardNumber(), accountPage.rows().get(0).cardNumber());
        CardListFilters card = new CardListFilters(CardListInputEditor.FilterFlag.BLANK,
                "0".repeat(11), CardListInputEditor.FilterFlag.ISVALID, chosen.cardNumber());
        assertEquals(1, pager.readForward("", card, 1).rows().stream().filter(row -> row != null).count());
        CardListFilters bothAgree = new CardListFilters(CardListInputEditor.FilterFlag.ISVALID,
                chosen.cardAcctId(), CardListInputEditor.FilterFlag.ISVALID, chosen.cardNumber());
        assertEquals(1, pager.readForward("", bothAgree, 1).rows().stream().filter(row -> row != null).count());
        CardListFilters bothDisagree = new CardListFilters(CardListInputEditor.FilterFlag.ISVALID,
                chosen.cardAcctId(), CardListInputEditor.FilterFlag.ISVALID, records.get(1).cardNumber());
        assertTrue(pager.readForward("", bothDisagree, 1).rows().stream().allMatch(row -> row == null));
        CardListFilters accountDisagrees = new CardListFilters(CardListInputEditor.FilterFlag.ISVALID,
                records.get(1).cardAcctId(), CardListInputEditor.FilterFlag.ISVALID, chosen.cardNumber());
        assertTrue(pager.readForward("", accountDisagrees, 1).rows().stream().allMatch(row -> row == null));
    }

    @Test
    void inputEditorReproducesBlankInvalidValidAndSuppressionRules() {
        CardListInputEditor editor = new CardListInputEditor();
        CardListInputEditor.InputEditResult blank = editor.edit("           ", "0000000000000000");
        assertEquals(CardListInputEditor.FilterFlag.BLANK, blank.accountFilter());
        assertEquals(CardListInputEditor.FilterFlag.BLANK, blank.cardFilter());
        assertTrue(blank.inputOk());
        assertEquals("00000000000", blank.accountId());
        assertEquals("0000000000000000", blank.cardNumber());
        CardListInputEditor.InputEditResult low = editor.edit("\0".repeat(11), "\0".repeat(16));
        assertEquals(CardListInputEditor.FilterFlag.BLANK, low.accountFilter());
        CardListInputEditor.InputEditResult invalid = editor.edit("12A", "12B");
        assertFalse(invalid.inputOk());
        assertEquals(CardListInputEditor.FilterFlag.NOT_OK, invalid.accountFilter());
        assertEquals("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER", invalid.errorMessage());
        assertEquals("00000000000", invalid.accountId());
        assertEquals("0000000000000000", invalid.cardNumber());
        CardListInputEditor.InputEditResult valid = editor.edit("00000000050", "0500024453765740");
        assertTrue(valid.inputOk());
        assertEquals(CardListInputEditor.FilterFlag.ISVALID, valid.accountFilter());
        assertEquals(CardListInputEditor.FilterFlag.ISVALID, valid.cardFilter());
        CardListInputEditor.InputEditResult cardInvalid = editor.edit("00000000050", "not-a-card");
        assertEquals("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER", cardInvalid.errorMessage());
    }

    @Test
    void fileLengthAndGteqAnchorFailureModesAreClear() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CardMaster(Arrays.copyOf(fixtureBytes(), 149)));
        assertTrue(error.getMessage().contains("not a multiple of 150"));
        List<CardRecord> records = fixtureRecords();
        String anchor = findAnchorBetween(records);
        CardListPage page = new CardListPager(new CardMaster(fixtureBytes()))
                .readForward(anchor, CardListFilters.none(), 1);
        int next = records.indexOf(records.stream().filter(r -> r.cardNumber().compareTo(anchor) > 0)
                .findFirst().orElseThrow());
        assertEquals(records.get(next).cardNumber(), page.rows().get(0).cardNumber());
        CardListPage afterEnd = new CardListPager(new CardMaster(fixtureBytes()))
                .readForward("9999999999999999", CardListFilters.none(), 1);
        assertFalse(afterEnd.nextPageExists());
        assertTrue(afterEnd.rows().stream().allMatch(row -> row == null));
    }

    @Test
    void batchReaderReadsFixtureAndPreservesCbaOutcome() throws Exception {
        CardFileBatchReader.BatchOutcome outcome = CardFileBatchReader.fromBytes(fixtureBytes()).readAll();
        assertEquals(50, outcome.records().size());
        assertEquals("10", outcome.terminalFileStatus());
        assertEquals(16, outcome.applResult());
        assertFalse(outcome.abended());
        assertTrue(outcome.displayLines().isEmpty());
    }

    @Test
    void batchReaderReportsInjectedOpenReadCloseAndNineStatuses() {
        CardFileBatchReader.BatchOutcome open = new CardFileBatchReader(new StubIo("37",
                List.of(), "00")).readAll();
        assertTrue(open.abended());
        assertEquals(List.of("ERROR OPENING CARDFILE", "ABENDING PROGRAM"), open.displayLines());

        CardFileBatchReader.BatchOutcome read = new CardFileBatchReader(new StubIo("00",
                List.of(new CardFileBatchReader.ReadResult("37", null)), "00")).readAll();
        assertEquals(12, read.applResult());
        assertEquals(List.of("ERROR READING CARDFILE", "FILE STATUS IS: 0037",
                "ABENDING PROGRAM"), read.displayLines());
        CardFileBatchReader.BatchOutcome read92 = new CardFileBatchReader(new StubIo("00",
                List.of(new CardFileBatchReader.ReadResult("92", null)), "00")).readAll();
        assertEquals("FILE STATUS IS: 9050", read92.displayLines().get(1));

        CardFileBatchReader.BatchOutcome nine = new CardFileBatchReader(new StubIo("00",
                List.of(new CardFileBatchReader.ReadResult("9x", null)), "00")).readAll();
        assertEquals("FILE STATUS IS: 9120", nine.displayLines().get(1));

        CardFileBatchReader.BatchOutcome close = new CardFileBatchReader(new StubIo("00",
                List.of(new CardFileBatchReader.ReadResult("10", null)), "35")).readAll();
        assertEquals(List.of("ERROR CLOSING CARDFILE", "ABENDING PROGRAM"), close.displayLines());
    }

    @Test
    void syntheticAccountWithMoreThanSevenCardsTraversesFilteredPages() {
        List<CardRecord> records = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> record(String.format("%016d", i), "00000000999", "Y")).toList();
        CardListPager pager = new CardListPager(new CardMaster(records));
        CardListFilters filter = new CardListFilters(CardListInputEditor.FilterFlag.ISVALID,
                "00000000999", CardListInputEditor.FilterFlag.BLANK, "0".repeat(16));
        CardListPage first = pager.readForward("", filter, 1);
        CardListPage second = pager.readForward(first.lastAnchor().cardNumber(), filter, 1);
        assertEquals(7, first.rows().stream().filter(row -> row != null).count());
        assertEquals(1, second.rows().stream().filter(row -> row != null).count());
    }

    @Test
    void syntheticNonYStatusIsReturnedVerbatim() {
        CardRecord record = record("0000000000000001", "00000000001", "N");
        CardListPage page = new CardListPager(new CardMaster(List.of(record)))
                .readForward("", CardListFilters.none(), 1);
        assertEquals("N", page.rows().get(0).activeStatus());
    }

    @Test
    void syntheticFillerF0RoundTripsWithoutNormalization() {
        byte[] filler = new byte[59];
        Arrays.fill(filler, (byte) 0xf0);
        CardRecord record = record("0000000000000001", "00000000001", "Y", filler);
        assertArrayEquals(filler, CODEC.decode(CODEC.encode(record)).filler());
    }

    private static List<CardListPage> forwardPages(CardListPager pager) throws Exception {
        List<CardListPage> pages = new ArrayList<>();
        String anchor = "";
        int screen = 0;
        do {
            CardListPage page = pager.readForward(anchor, CardListFilters.none(), screen++);
            pages.add(page);
            anchor = page.lastAnchor().cardNumber();
        } while (pages.get(pages.size() - 1).nextPageExists());
        return pages;
    }

    private static String findAnchorBetween(List<CardRecord> records) {
        for (int i = 0; i + 1 < records.size(); i++) {
            long left = Long.parseLong(records.get(i).cardNumber());
            long right = Long.parseLong(records.get(i + 1).cardNumber());
            if (right - left > 1) {
                return String.format("%016d", left + (right - left) / 2);
            }
        }
        throw new AssertionError("fixture has no concrete key gap");
    }

    private static List<CardRecord> fixtureRecords() throws Exception {
        byte[] bytes = fixtureBytes();
        List<CardRecord> records = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += 150) {
            records.add(CODEC.decode(Arrays.copyOfRange(bytes, offset, offset + 150)));
        }
        return records;
    }

    private static byte[] fixtureBytes() throws Exception {
        return Files.readAllBytes(EBCDIC_FIXTURE);
    }

    private static CardRecord record(String cardNumber, String accountId, String status) {
        return record(cardNumber, accountId, status, new byte[59]);
    }

    private static CardRecord record(String cardNumber, String accountId, String status, byte[] filler) {
        return new CardRecord(cardNumber, accountId, "000", "Synthetic".repeat(5) + "12345",
                "2023-03-09", status, filler);
    }

    private static final class StubIo implements CardFileBatchReader.CardFileIo {
        private final String open;
        private final List<CardFileBatchReader.ReadResult> reads;
        private final String close;
        private int index;

        private StubIo(String open, List<CardFileBatchReader.ReadResult> reads, String close) {
            this.open = open;
            this.reads = reads;
            this.close = close;
        }

        @Override
        public String open() {
            return open;
        }

        @Override
        public CardFileBatchReader.ReadResult readNext() {
            return index < reads.size() ? reads.get(index++) : new CardFileBatchReader.ReadResult("10", null);
        }

        @Override
        public String close() {
            return close;
        }
    }
}
