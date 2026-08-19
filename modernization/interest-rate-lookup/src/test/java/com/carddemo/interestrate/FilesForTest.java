package com.carddemo.interestrate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class FilesForTest {
    private FilesForTest() {
    }

    static String readFirstLine(Path path) throws IOException {
        return Files.readAllLines(path).get(0);
    }
}
