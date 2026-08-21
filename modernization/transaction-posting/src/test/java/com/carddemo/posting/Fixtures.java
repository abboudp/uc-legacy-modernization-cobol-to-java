package com.carddemo.posting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Access to the golden-master datasets in {@code app/data/ASCII}, which are the same records the legacy
 * batch loads into VSAM before {@code POSTTRAN} runs. They are read, never written.
 */
final class Fixtures {

    private static final Path ASCII_DATA = locate();

    private Fixtures() {
    }

    static List<String> dailyTransactions() {
        return lines("dailytran.txt");
    }

    static List<String> accounts() {
        return lines("acctdata.txt");
    }

    static List<String> cardXrefs() {
        return lines("cardxref.txt");
    }

    static List<String> categoryBalances() {
        return lines("tcatbal.txt");
    }

    /** Reads a fixture, dropping the CR of the CRLF-terminated members so records are record-length exact. */
    static List<String> lines(String name) {
        try {
            return Files.readAllLines(ASCII_DATA.resolve(name), StandardCharsets.ISO_8859_1).stream()
                    .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
                    .filter(line -> !line.isBlank())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read fixture " + name, e);
        }
    }

    private static Path locate() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path data = candidate.resolve("app/data/ASCII");
            if (Files.isDirectory(data)) {
                return data;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Cannot locate app/data/ASCII above " + Path.of("").toAbsolutePath());
    }
}
