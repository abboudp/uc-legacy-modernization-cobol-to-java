package com.carddemo.interestrate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class FixtureSupport {
    private static final Path RELATIVE_FIXTURE =
            Path.of("app", "data", "EBCDIC", "AWS.M2.CARDDEMO.DISCGRP.PS");
    private static final Path RELATIVE_ASCII =
            Path.of("app", "data", "ASCII", "discgrp.txt");

    private FixtureSupport() {
    }

    static Path fixture() {
        return find(RELATIVE_FIXTURE);
    }

    static Path asciiFixture() {
        return find(RELATIVE_ASCII);
    }

    private static Path find(Path relative) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository fixture " + relative);
    }

    static byte[] read(Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
