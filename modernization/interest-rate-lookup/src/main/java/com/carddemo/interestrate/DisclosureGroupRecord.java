package com.carddemo.interestrate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

/**
 * Typed CVTRA02Y DIS-GROUP-RECORD, including its physical filler bytes.
 */
public final class DisclosureGroupRecord {
    public static final int RECORD_LENGTH = 50;
    public static final int GROUP_ID_WIDTH = 10;
    public static final int TYPE_CODE_WIDTH = 2;
    public static final int CATEGORY_CODE_WIDTH = 4;
    public static final int RATE_WIDTH = 6;
    public static final int FILLER_WIDTH = 28;

    private final String disAcctGroupId;
    private final String disTranTypeCd;
    private final String disTranCatCd;
    private final BigDecimal disIntRate;
    private final byte[] filler;

    public DisclosureGroupRecord(
            String disAcctGroupId,
            String disTranTypeCd,
            String disTranCatCd,
            BigDecimal disIntRate,
            byte[] filler) {
        this.disAcctGroupId = fixedWidth(disAcctGroupId, GROUP_ID_WIDTH, "DIS-ACCT-GROUP-ID");
        this.disTranTypeCd = fixedWidth(disTranTypeCd, TYPE_CODE_WIDTH, "DIS-TRAN-TYPE-CD");
        this.disTranCatCd = fixedWidth(disTranCatCd, CATEGORY_CODE_WIDTH, "DIS-TRAN-CAT-CD");
        this.disIntRate = Objects.requireNonNull(disIntRate, "DIS-INT-RATE").setScale(2);
        if (filler == null || filler.length != FILLER_WIDTH) {
            throw new IllegalArgumentException("FILLER must contain exactly 28 bytes");
        }
        this.filler = filler.clone();
    }

    public String disAcctGroupId() {
        return disAcctGroupId;
    }

    public String disTranTypeCd() {
        return disTranTypeCd;
    }

    public String disTranCatCd() {
        return disTranCatCd;
    }

    public BigDecimal disIntRate() {
        return disIntRate;
    }

    public byte[] filler() {
        return filler.clone();
    }

    public DisclosureGroupKey key() {
        return new DisclosureGroupKey(disAcctGroupId, disTranTypeCd, disTranCatCd);
    }

    public byte[] toBytes() {
        return DisclosureGroupRecordCodec.encode(this);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DisclosureGroupRecord that)) {
            return false;
        }
        return disAcctGroupId.equals(that.disAcctGroupId)
                && disTranTypeCd.equals(that.disTranTypeCd)
                && disTranCatCd.equals(that.disTranCatCd)
                && disIntRate.equals(that.disIntRate)
                && Arrays.equals(filler, that.filler);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(disAcctGroupId, disTranTypeCd, disTranCatCd, disIntRate);
        return 31 * result + Arrays.hashCode(filler);
    }

    private static String fixedWidth(String value, int width, String fieldName) {
        if (value == null || value.length() != width) {
            throw new IllegalArgumentException(fieldName + " must contain exactly " + width + " characters");
        }
        return value;
    }
}
