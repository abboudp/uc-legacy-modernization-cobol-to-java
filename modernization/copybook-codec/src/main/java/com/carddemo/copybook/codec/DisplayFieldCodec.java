package com.carddemo.copybook.codec;

public final class DisplayFieldCodec {
    private DisplayFieldCodec() {
    }

    public static String decode(byte[] bytes, int offset, int length) {
        return EbcdicCodec.decodeDisplay(bytes, offset, length);
    }

    public static byte[] encode(String value, int length) {
        return EbcdicCodec.encodeDisplay(value, length);
    }
}
