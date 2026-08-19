package com.carddemo.interestrate;

@FunctionalInterface
public interface DisclosureGroupReader {
    ReadResult read(DisclosureGroupKey key);
}
