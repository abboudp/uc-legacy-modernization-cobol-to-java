package com.carddemo.posting;

import java.time.LocalDateTime;

/**
 * The {@code Z-GET-DB2-FORMAT-TIMESTAMP} paragraph: 26 characters of
 * {@code CCYY-MM-DD-HH.MM.SS.hh0000}, where {@code hh} is hundredths of a second (all that
 * {@code FUNCTION CURRENT-DATE} supplies) and the last four positions are literal zeros.
 */
public final class Db2Timestamp {

    public static final int LENGTH = 26;

    private Db2Timestamp() {
    }

    public static String format(LocalDateTime moment) {
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                moment.getYear(),
                moment.getMonthValue(),
                moment.getDayOfMonth(),
                moment.getHour(),
                moment.getMinute(),
                moment.getSecond(),
                moment.getNano() / 10_000_000);
    }
}
