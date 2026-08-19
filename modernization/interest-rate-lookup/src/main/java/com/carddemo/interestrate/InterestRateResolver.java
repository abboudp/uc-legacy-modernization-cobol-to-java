package com.carddemo.interestrate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implements 1200-GET-INTEREST-RATE and 1200-A-GET-DEFAULT-INT-RATE as values.
 */
public final class InterestRateResolver {
    public static final String DEFAULT_GROUP_ID = "DEFAULT   ";
    private final DisclosureGroupReader reader;

    public InterestRateResolver(DisclosureGroupReader reader) {
        this.reader = reader;
    }

    public InterestRateResolution resolve(DisclosureGroupKey requestedKey) {
        ReadResult requested = reader.read(requestedKey);
        if ("00".equals(requested.fileStatus())) {
            return new InterestRateResolution(
                    ResolutionPath.DIRECT_HIT,
                    List.of("00"),
                    requiredRate(requested),
                    List.of());
        }
        if (!"23".equals(requested.fileStatus())) {
            return new InterestRateResolution(
                    ResolutionPath.ABEND_READ_ERROR,
                    List.of(requested.fileStatus()),
                    Optional.empty(),
                    List.of("ERROR READING DISCLOSURE GROUP FILE"));
        }

        List<String> messages = new ArrayList<>();
        messages.add("DISCLOSURE GROUP RECORD MISSING");
        messages.add("TRY WITH DEFAULT GROUP CODE");
        DisclosureGroupKey defaultKey = new DisclosureGroupKey(
                DEFAULT_GROUP_ID, requestedKey.typeCode(), requestedKey.categoryCode());
        ReadResult fallback = reader.read(defaultKey);
        if ("00".equals(fallback.fileStatus())) {
            return new InterestRateResolution(
                    ResolutionPath.DEFAULT_FALLBACK,
                    List.of(requested.fileStatus(), fallback.fileStatus()),
                    requiredRate(fallback),
                    messages);
        }
        messages.add("ERROR READING DEFAULT DISCLOSURE GROUP");
        return new InterestRateResolution(
                ResolutionPath.ABEND_DEFAULT_MISSING,
                List.of(requested.fileStatus(), fallback.fileStatus()),
                Optional.empty(),
                messages);
    }

    private static Optional<BigDecimal> requiredRate(ReadResult result) {
        return Optional.of(result.record()
                .orElseThrow(() -> new IllegalStateException("Successful read has no record"))
                .disIntRate());
    }
}
