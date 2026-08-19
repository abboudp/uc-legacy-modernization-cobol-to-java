package com.abboudp.cardxref;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Read-only loader for a fixed-width CARDXREF physical file.
 */
public final class CardXrefFile {
    public static final int RECORD_LENGTH = CardXrefRecord.RECORD_LENGTH;

    private final Path path;
    private final List<CardXrefRecord> records;

    private CardXrefFile(Path path, List<CardXrefRecord> records) {
        this.path = path;
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
    }

    public static CardXrefFile load(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length % RECORD_LENGTH != 0) {
            throw new IllegalArgumentException(
                    "CARDXREF file length must be a multiple of 50 bytes, got " + bytes.length);
        }

        List<CardXrefRecord> records = new ArrayList<>(bytes.length / RECORD_LENGTH);
        for (int offset = 0; offset < bytes.length; offset += RECORD_LENGTH) {
            byte[] raw = new byte[RECORD_LENGTH];
            System.arraycopy(bytes, offset, raw, 0, RECORD_LENGTH);
            records.add(CardXrefRecord.decode(raw));
        }
        return new CardXrefFile(path, records);
    }

    public Path path() {
        return path;
    }

    public List<CardXrefRecord> records() {
        return records;
    }

    public CardXrefReadModel readModel() {
        return new CardXrefReadModel(records);
    }
}
