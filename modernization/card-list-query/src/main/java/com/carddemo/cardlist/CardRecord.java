package com.carddemo.cardlist;

import java.util.Arrays;
import java.util.Objects;

public final class CardRecord {
    private final String cardNumber;
    private final String cardAcctId;
    private final String cardCvvCd;
    private final String embossedName;
    private final String cardExpirationDate;
    private final String cardActiveStatus;
    private final byte[] filler;

    public CardRecord(String cardNumber, String cardAcctId, String cardCvvCd,
                      String embossedName, String cardExpirationDate,
                      String cardActiveStatus, byte[] filler) {
        this.cardNumber = requireWidth(cardNumber, 16, "CARD-NUM");
        this.cardAcctId = requireWidth(cardAcctId, 11, "CARD-ACCT-ID");
        this.cardCvvCd = requireWidth(cardCvvCd, 3, "CARD-CVV-CD");
        this.embossedName = requireWidth(embossedName, 50, "CARD-EMBOSSED-NAME");
        this.cardExpirationDate = requireWidth(cardExpirationDate, 10, "CARD-EXPIRAION-DATE");
        this.cardActiveStatus = requireWidth(cardActiveStatus, 1, "CARD-ACTIVE-STATUS");
        Objects.requireNonNull(filler, "filler");
        if (filler.length != 59) {
            throw new IllegalArgumentException("FILLER must be exactly 59 bytes");
        }
        this.filler = filler.clone();
    }

    private static String requireWidth(String value, int width, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() != width) {
            throw new IllegalArgumentException(field + " must be exactly " + width + " characters");
        }
        return value;
    }

    public String cardNumber() {
        return cardNumber;
    }

    public String cardAcctId() {
        return cardAcctId;
    }

    public String cardCvvCd() {
        return cardCvvCd;
    }

    public String embossedName() {
        return embossedName;
    }

    /** CVACT02Y.cpy:9 misspells CARD-EXPIRAION-DATE; Java keeps the corrected accessor name. */
    public String cardExpirationDate() {
        return cardExpirationDate;
    }

    public String cardActiveStatus() {
        return cardActiveStatus;
    }

    public byte[] filler() {
        return filler.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CardRecord that)) {
            return false;
        }
        return cardNumber.equals(that.cardNumber)
                && cardAcctId.equals(that.cardAcctId)
                && cardCvvCd.equals(that.cardCvvCd)
                && embossedName.equals(that.embossedName)
                && cardExpirationDate.equals(that.cardExpirationDate)
                && cardActiveStatus.equals(that.cardActiveStatus)
                && Arrays.equals(filler, that.filler);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(cardNumber, cardAcctId, cardCvvCd, embossedName,
                cardExpirationDate, cardActiveStatus);
        return 31 * result + Arrays.hashCode(filler);
    }
}
