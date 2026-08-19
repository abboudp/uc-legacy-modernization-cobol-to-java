package com.carddemo.interestrate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record InterestRateResolution(
        ResolutionPath path,
        List<String> fileStatuses,
        Optional<BigDecimal> rate,
        List<String> displayMessages) {
    public InterestRateResolution {
        if (path == null || fileStatuses == null || rate == null || displayMessages == null) {
            throw new IllegalArgumentException("Resolution fields must not be null");
        }
        fileStatuses = List.copyOf(fileStatuses);
        displayMessages = List.copyOf(displayMessages);
    }
}
