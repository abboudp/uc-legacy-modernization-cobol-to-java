package com.carddemo.signon;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads the repository's own {@code USRSEC} data as the test seed.
 *
 * <p>The bytes are those of {@code app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS} (800 bytes, 10 records
 * of 80), transcoded once to ASCII with {@code iconv -f IBM-1047 -t ASCII} and committed here as
 * {@code AWS.M2.CARDDEMO.USRSEC.ASCII.PS}; the repository ships no ASCII copy of this dataset, and
 * this module deliberately owns no character-set codec.
 */
final class UsrsecFixture {

    static final String RESOURCE = "/AWS.M2.CARDDEMO.USRSEC.ASCII.PS";

    private UsrsecFixture() {
    }

    static String contents() {
        try (InputStream in = UsrsecFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<LegacyUsrsecRecord> records() {
        return LegacyUsrsecRecord.parseAll(contents());
    }
}
