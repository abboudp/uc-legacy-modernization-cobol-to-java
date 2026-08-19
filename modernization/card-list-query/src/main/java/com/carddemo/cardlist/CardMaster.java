package com.carddemo.cardlist;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public final class CardMaster {
    private final NavigableMap<String, CardRecord> records;

    public CardMaster(byte[] fileBytes) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        if (fileBytes.length % CardRecordCodec.RECORD_LENGTH != 0) {
            throw new IllegalArgumentException("CARDDATA length " + fileBytes.length
                    + " is not a multiple of 150");
        }
        CardRecordCodec codec = new CardRecordCodec();
        NavigableMap<String, CardRecord> loaded = new TreeMap<>();
        for (int offset = 0; offset < fileBytes.length; offset += CardRecordCodec.RECORD_LENGTH) {
            byte[] raw = java.util.Arrays.copyOfRange(fileBytes, offset,
                    offset + CardRecordCodec.RECORD_LENGTH);
            CardRecord record = codec.decode(raw);
            if (loaded.put(record.cardNumber(), record) != null) {
                throw new IllegalArgumentException("duplicate CARD-NUM: " + record.cardNumber());
            }
        }
        records = Collections.unmodifiableNavigableMap(loaded);
    }

    public CardMaster(Collection<CardRecord> source) {
        Objects.requireNonNull(source, "source");
        NavigableMap<String, CardRecord> loaded = new TreeMap<>();
        for (CardRecord record : source) {
            Objects.requireNonNull(record, "record");
            if (loaded.put(record.cardNumber(), record) != null) {
                throw new IllegalArgumentException("duplicate CARD-NUM: " + record.cardNumber());
            }
        }
        records = Collections.unmodifiableNavigableMap(loaded);
    }

    public Map.Entry<String, CardRecord> ceilingEntry(String key) {
        return records.ceilingEntry(key);
    }

    public Map.Entry<String, CardRecord> lowerEntry(String key) {
        return records.lowerEntry(key);
    }

    Map.Entry<String, CardRecord> ceilingEntryAfter(String key) {
        return records.higherEntry(key);
    }

    public Map.Entry<String, CardRecord> lastEntry() {
        return records.lastEntry();
    }

    public int size() {
        return records.size();
    }
}
