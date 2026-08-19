package com.carddemo.cardlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.nio.charset.Charset;

public final class CardFileBatchReader {
    private static final Charset EBCDIC = Charset.forName("IBM037");
    public interface CardFileIo {
        String open();
        ReadResult readNext();
        String close();
    }

    public record ReadResult(String status, byte[] record) {
        public ReadResult {
            Objects.requireNonNull(status, "status");
            if (record != null) {
                record = record.clone();
            }
        }
    }

    public record BatchOutcome(List<CardRecord> records, String terminalFileStatus,
                               int applResult, boolean abended, List<String> displayLines) {
        public BatchOutcome {
            records = List.copyOf(records);
            displayLines = List.copyOf(displayLines);
        }
    }

    private final CardFileIo io;
    private final CardRecordCodec codec;

    public CardFileBatchReader(CardFileIo io) {
        this(io, new CardRecordCodec());
    }

    public CardFileBatchReader(CardFileIo io, CardRecordCodec codec) {
        this.io = Objects.requireNonNull(io, "io");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public BatchOutcome readAll() {
        List<CardRecord> records = new ArrayList<>();
        List<String> displays = new ArrayList<>();
        String openStatus = normalized(io.open());
        if (!"00".equals(openStatus)) {
            displays.add("ERROR OPENING CARDFILE");
            displays.add("ABENDING PROGRAM");
            return new BatchOutcome(records, openStatus, 12, true, displays);
        }

        String terminalStatus = "00";
        int applResult = 16;
        boolean abended = false;
        while (true) {
            ReadResult read = io.readNext();
            terminalStatus = normalized(read.status());
            if ("00".equals(terminalStatus)) {
                if (read.record() == null) {
                    terminalStatus = "12";
                    displays.add("ERROR READING CARDFILE");
                    displays.add("FILE STATUS IS: " + formatFileStatus(terminalStatus));
                    displays.add("ABENDING PROGRAM");
                    applResult = 12;
                    abended = true;
                    break;
                }
                records.add(codec.decode(read.record()));
            } else if ("10".equals(terminalStatus)) {
                applResult = 16;
                break;
            } else {
                displays.add("ERROR READING CARDFILE");
                displays.add("FILE STATUS IS: " + formatFileStatus(terminalStatus));
                displays.add("ABENDING PROGRAM");
                applResult = 12;
                abended = true;
                return new BatchOutcome(records, terminalStatus, applResult, abended, displays);
            }
        }

        String closeStatus = normalized(io.close());
        if (!"00".equals(closeStatus)) {
            terminalStatus = closeStatus;
            displays.add("ERROR CLOSING CARDFILE");
            displays.add("ABENDING PROGRAM");
            applResult = 12;
            abended = true;
        }
        return new BatchOutcome(records, terminalStatus, applResult, abended, displays);
    }

    public static CardFileBatchReader fromBytes(byte[] bytes) {
        return new CardFileBatchReader(new ByteArrayCardFileIo(bytes));
    }

    static String formatFileStatus(String status) {
        if (status != null && status.length() == 2
                && status.charAt(0) != '9'
                && Character.isDigit(status.charAt(0))
                && Character.isDigit(status.charAt(1))) {
            return "00" + status;
        }
        if (status == null || status.isEmpty()) {
            return "0000";
        }
        char first = status.charAt(0);
        char second = status.length() > 1 ? status.charAt(1) : 0;
        int ebcdicCode = EBCDIC.encode(String.valueOf(second)).get(0) & 0xff;
        return first + String.format("%03d", ebcdicCode);
    }

    private static String normalized(String status) {
        return status == null ? "" : status;
    }

    private static final class ByteArrayCardFileIo implements CardFileIo {
        private final byte[] bytes;
        private int offset;

        private ByteArrayCardFileIo(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes).clone();
        }

        @Override
        public String open() {
            return "00";
        }

        @Override
        public ReadResult readNext() {
            if (offset == bytes.length) {
                return new ReadResult("10", null);
            }
            if (bytes.length - offset < CardRecordCodec.RECORD_LENGTH) {
                return new ReadResult("92", null);
            }
            byte[] record = java.util.Arrays.copyOfRange(bytes, offset,
                    offset + CardRecordCodec.RECORD_LENGTH);
            offset += CardRecordCodec.RECORD_LENGTH;
            return new ReadResult("00", record);
        }

        @Override
        public String close() {
            return "00";
        }
    }
}
