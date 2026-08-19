package com.carddemo.copybook.codec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FixedLengthRecords {
    private FixedLengthRecords() {
    }

    public static List<byte[]> split(byte[] image, int recordLength) {
        if (recordLength <= 0) {
            throw new CopybookCodecException("Record length must be positive: " + recordLength);
        }
        if (image.length % recordLength != 0) {
            throw new CopybookCodecException(
                    "File length is not a multiple of record length: expected a multiple of "
                            + recordLength + ", actual " + image.length);
        }
        List<byte[]> records = new ArrayList<>(image.length / recordLength);
        for (int offset = 0; offset < image.length; offset += recordLength) {
            records.add(java.util.Arrays.copyOfRange(image, offset, offset + recordLength));
        }
        return List.copyOf(records);
    }

    public static List<byte[]> read(Path path, int recordLength) {
        try {
            return split(Files.readAllBytes(path), recordLength);
        } catch (IOException exception) {
            throw new CopybookCodecException("Unable to read fixed-length image " + path, exception);
        }
    }
}
