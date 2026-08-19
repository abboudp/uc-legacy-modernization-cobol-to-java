package com.carddemo.interestrate;

import java.util.Optional;

/** Two-character COBOL file status and the record returned by a READ, when present. */
public record ReadResult(String fileStatus, Optional<DisclosureGroupRecord> record) {
    public ReadResult {
        if (fileStatus == null || !fileStatus.matches("[0-9A-Z]{2}")) {
            throw new IllegalArgumentException("File status must be exactly two characters: " + fileStatus);
        }
        if (record == null) {
            throw new IllegalArgumentException("Read result record optional must not be null");
        }
    }

    public static ReadResult found(DisclosureGroupRecord record) {
        return new ReadResult("00", Optional.of(record));
    }

    public static ReadResult missing() {
        return new ReadResult("23", Optional.empty());
    }
}
