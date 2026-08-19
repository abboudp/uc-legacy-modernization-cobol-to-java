package com.carddemo.copybook.codec;

/**
 * Indicates invalid bytes, field values, or record framing.
 */
public class CopybookCodecException extends RuntimeException {
    public CopybookCodecException(String message) {
        super(message);
    }

    public CopybookCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
