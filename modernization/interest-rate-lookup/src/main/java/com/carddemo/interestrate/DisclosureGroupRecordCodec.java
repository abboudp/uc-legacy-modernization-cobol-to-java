package com.carddemo.interestrate;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-width CVTRA02Y codec. Character fields use IBM037; the rate is signed zoned.
 */
public final class DisclosureGroupRecordCodec {
    private static final Charset IBM037 = Charset.forName("IBM037");
    private DisclosureGroupRecordCodec() {
    }

    public static DisclosureGroupRecord decode(byte[] bytes) {
        if (bytes == null || bytes.length != DisclosureGroupRecord.RECORD_LENGTH) {
            throw new IllegalArgumentException("Disclosure group record must contain exactly 50 bytes");
        }
        String group = decodeText(bytes, 0, DisclosureGroupRecord.GROUP_ID_WIDTH);
        String type = decodeText(bytes, 10, DisclosureGroupRecord.TYPE_CODE_WIDTH);
        String category = decodeText(bytes, 12, DisclosureGroupRecord.CATEGORY_CODE_WIDTH);
        return new DisclosureGroupRecord(
                group,
                type,
                category,
                SignedZonedDecimalCodec.decode(bytes, 16),
                slice(bytes, 22, DisclosureGroupRecord.FILLER_WIDTH));
    }

    public static byte[] encode(DisclosureGroupRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Disclosure group record must not be null");
        }
        byte[] bytes = new byte[DisclosureGroupRecord.RECORD_LENGTH];
        putText(bytes, 0, record.disAcctGroupId(), DisclosureGroupRecord.GROUP_ID_WIDTH);
        putText(bytes, 10, record.disTranTypeCd(), DisclosureGroupRecord.TYPE_CODE_WIDTH);
        putText(bytes, 12, record.disTranCatCd(), DisclosureGroupRecord.CATEGORY_CODE_WIDTH);
        byte[] rate = SignedZonedDecimalCodec.encode(record.disIntRate());
        System.arraycopy(rate, 0, bytes, 16, rate.length);
        System.arraycopy(record.filler(), 0, bytes, 22, DisclosureGroupRecord.FILLER_WIDTH);
        return bytes;
    }

    public static List<DisclosureGroupRecord> decodeAll(byte[] bytes) {
        if (bytes == null || bytes.length % DisclosureGroupRecord.RECORD_LENGTH != 0) {
            throw new IllegalArgumentException("Disclosure group data length must be a multiple of 50 bytes");
        }
        List<DisclosureGroupRecord> records = new ArrayList<>(bytes.length / DisclosureGroupRecord.RECORD_LENGTH);
        for (int offset = 0; offset < bytes.length; offset += DisclosureGroupRecord.RECORD_LENGTH) {
            records.add(decode(slice(bytes, offset, DisclosureGroupRecord.RECORD_LENGTH)));
        }
        return List.copyOf(records);
    }

    private static String decodeText(byte[] bytes, int offset, int width) {
        try {
            return IBM037.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, width))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid IBM037 character field at offset " + offset, exception);
        }
    }

    private static void putText(byte[] target, int offset, String value, int width) {
        byte[] encoded = value.getBytes(IBM037);
        if (encoded.length != width) {
            throw new IllegalArgumentException("Character field at offset " + offset
                    + " must encode to exactly " + width + " bytes");
        }
        System.arraycopy(encoded, 0, target, offset, width);
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }
}
