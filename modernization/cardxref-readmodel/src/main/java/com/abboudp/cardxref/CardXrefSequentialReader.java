package com.abboudp.cardxref;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Small port of CBACT03C's OPEN, sequential READ, and CLOSE paragraphs.
 */
public final class CardXrefSequentialReader {
    private static final String SUCCESS = "00";
    private static final String END_OF_FILE = "10";
    private static final int ABEND_CODE = 999;

    private final Path path;
    private InputStream input;

    public CardXrefSequentialReader(Path path) {
        this.path = Objects.requireNonNull(path, "path must not be null");
    }

    public FileOutcome open() {
        if (input != null) {
            return abend("41", "XREFFILE is already open");
        }
        try {
            input = Files.newInputStream(path);
            return success(null);
        } catch (NoSuchFileException exception) {
            return abend("35", "XREFFILE does not exist: " + path);
        } catch (IOException exception) {
            return abend("30", "Unable to open XREFFILE: " + exception.getMessage());
        }
    }

    public FileOutcome readNext() {
        if (input == null) {
            return abend("41", "XREFFILE is not open");
        }
        try {
            byte[] raw = input.readNBytes(CardXrefRecord.RECORD_LENGTH);
            if (raw.length == 0) {
                return eof();
            }
            if (raw.length != CardXrefRecord.RECORD_LENGTH) {
                return abend("92", "Truncated CARDXREF record: " + raw.length + " bytes");
            }
            return success(CardXrefRecord.decode(raw));
        } catch (IOException exception) {
            return abend("92", "Unable to read XREFFILE: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return abend("92", "Invalid CARDXREF record: " + exception.getMessage());
        }
    }

    public FileOutcome close() {
        if (input == null) {
            return success(null);
        }
        try {
            input.close();
            input = null;
            return success(null);
        } catch (IOException exception) {
            input = null;
            return abend("38", "Unable to close XREFFILE: " + exception.getMessage());
        }
    }

    public static String formattedFileStatus(String status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status.length() == 2
                && Character.isDigit(status.charAt(0))
                && Character.isDigit(status.charAt(1))
                && status.charAt(0) != '9') {
            return "00" + status;
        }
        int first = status.isEmpty() ? 0 : status.charAt(0);
        int second = status.length() < 2 ? 0 : status.charAt(1);
        return String.format("%02d%02d", first, second);
    }

    private static FileOutcome success(CardXrefRecord record) {
        return new FileOutcome(SUCCESS, record, false, false, 0, null);
    }

    private static FileOutcome eof() {
        return new FileOutcome(END_OF_FILE, null, true, false, 0, null);
    }

    private static FileOutcome abend(String status, String detail) {
        return new FileOutcome(
                status,
                null,
                false,
                true,
                ABEND_CODE,
                "FILE STATUS IS: " + formattedFileStatus(status) + "; " + detail);
    }

    public record FileOutcome(
            String status,
            CardXrefRecord record,
            boolean endOfFile,
            boolean abended,
            int abendCode,
            String message) {
        public boolean isSuccess() {
            return SUCCESS.equals(status);
        }

        public boolean isEof() {
            return END_OF_FILE.equals(status) && endOfFile;
        }
    }
}
