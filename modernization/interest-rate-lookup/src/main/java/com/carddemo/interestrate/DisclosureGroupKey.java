package com.carddemo.interestrate;

import java.util.Objects;

/** The 16-byte KEYS(16 0) composite key. */
public record DisclosureGroupKey(String groupId, String typeCode, String categoryCode) {
    public DisclosureGroupKey {
        groupId = fixed(groupId, DisclosureGroupRecord.GROUP_ID_WIDTH, "group id");
        typeCode = fixed(typeCode, DisclosureGroupRecord.TYPE_CODE_WIDTH, "type code");
        categoryCode = fixed(categoryCode, DisclosureGroupRecord.CATEGORY_CODE_WIDTH, "category code");
    }

    public static DisclosureGroupKey of(String groupId, String typeCode, String categoryCode) {
        return new DisclosureGroupKey(groupId, typeCode, categoryCode);
    }

    private static String fixed(String value, int width, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() != width) {
            throw new IllegalArgumentException(name + " must contain exactly " + width + " characters");
        }
        return value;
    }
}
