package com.carddemo.interestrate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only in-memory representation of the DISCGRP VSAM rows.
 */
public final class DisclosureGroupReadModel implements DisclosureGroupReader {
    private final Map<DisclosureGroupKey, DisclosureGroupRecord> records;

    private DisclosureGroupReadModel(List<DisclosureGroupRecord> records) {
        Map<DisclosureGroupKey, DisclosureGroupRecord> values = new LinkedHashMap<>();
        for (DisclosureGroupRecord record : records) {
            if (values.put(record.key(), record) != null) {
                throw new IllegalArgumentException("Duplicate disclosure group key: " + record.key());
            }
        }
        this.records = Map.copyOf(values);
    }

    public static DisclosureGroupReadModel fromBytes(byte[] bytes) {
        return new DisclosureGroupReadModel(DisclosureGroupRecordCodec.decodeAll(bytes));
    }

    public static DisclosureGroupReadModel fromPath(Path path) throws IOException {
        return fromBytes(Files.readAllBytes(path));
    }

    public int size() {
        return records.size();
    }

    @Override
    public ReadResult read(DisclosureGroupKey key) {
        DisclosureGroupRecord record = records.get(key);
        return record == null ? ReadResult.missing() : ReadResult.found(record);
    }
}
