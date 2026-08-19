package com.carddemo.cardlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CardListPager {
    private static final int PAGE_SIZE = 7;
    private static final String NO_MORE = "NO MORE RECORDS TO SHOW";
    private static final String NO_RECORDS = "NO RECORDS FOUND FOR THIS SEARCH CONDITION.";
    private static final String FILE_ERROR = "File Error:READ on CARDFILE";

    private final CardMaster master;

    public CardListPager(CardMaster master) {
        this.master = Objects.requireNonNull(master, "master");
    }

    public CardListPage readForward(String anchorCardNum, CardListFilters filters, int screenNum) {
        List<CardListRow> rows = blankRows();
        CardAnchor first = CardAnchor.empty();
        CardAnchor last = CardAnchor.empty();
        Map.Entry<String, CardRecord> cursor = master.ceilingEntry(anchorCardNum == null ? "" : anchorCardNum);
        CardRecord recordArea = null;
        boolean next = true;
        int count = 0;
        int resultingScreen = screenNum;
        String message = "";

        while (cursor != null && count < PAGE_SIZE) {
            recordArea = cursor.getValue();
            cursor = master.ceilingEntryAfter(cursor.getKey());
            if (!matches(recordArea, filters)) {
                continue;
            }
            rows.set(count, row(recordArea));
            count++;
            if (count == 1) {
                first = anchor(recordArea);
                // COCRDLIC.cbl:1177-1178 increments the screen only from zero.
                if (resultingScreen == 0) {
                    resultingScreen++;
                }
            }
            if (count == PAGE_SIZE) {
                last = anchor(recordArea);
                if (cursor != null) {
                    recordArea = cursor.getValue();
                    last = anchor(recordArea);
                    next = true;
                } else {
                    next = false;
                    message = NO_MORE;
                }
                return finish(rows, first, last, next, resultingScreen, message, count);
            }
        }

        next = false;
        if (recordArea != null) {
            last = anchor(recordArea);
        }
        message = NO_MORE;
        return finish(rows, first, last, next, resultingScreen, message, count);
    }

    public CardListPage readBackwards(String firstAnchorCardNum, CardListFilters filters, int screenNum) {
        List<CardListRow> rows = blankRows();
        CardAnchor first = anchorForKey(firstAnchorCardNum);
        CardAnchor last = first;
        int counter = PAGE_SIZE + 1;
        boolean next = true;
        Map.Entry<String, CardRecord> positioned = master.ceilingEntry(
                firstAnchorCardNum == null ? "" : firstAnchorCardNum);
        Map.Entry<String, CardRecord> discarded = positioned == null ? master.lastEntry() : positioned;
        if (discarded == null) {
            return errorPage(rows, first, last, next, screenNum);
        }
        Map.Entry<String, CardRecord> cursor = master.lowerEntry(discarded.getKey());
        counter--;
        while (counter > 0) {
            if (cursor == null) {
                // COCRDLIC.cbl:1361-1368 has no ENDFILE branch in this loop.
                return errorPage(rows, first, last, next, screenNum);
            }
            CardRecord record = cursor.getValue();
            cursor = master.lowerEntry(cursor.getKey());
            if (!matches(record, filters)) {
                continue;
            }
            rows.set(counter - 1, row(record));
            if (counter == 1) {
                first = anchor(record);
            }
            counter--;
        }
        return new CardListPage(rows, first, last, next, screenNum > 1, screenNum, "", false);
    }

    private CardListPage finish(List<CardListRow> rows, CardAnchor first, CardAnchor last,
                                boolean next, int screenNum, String message, int count) {
        boolean noRecords = screenNum == 1 && count == 0;
        if (noRecords) {
            // COCRDLIC.cbl:1216-1219 and :121-122 overwrite NO MORE with NO RECORDS.
            message = NO_RECORDS;
        }
        return new CardListPage(rows, first, last, next, screenNum > 1, screenNum, message, noRecords);
    }

    private CardListPage errorPage(List<CardListRow> rows, CardAnchor first, CardAnchor last,
                                   boolean next, int screenNum) {
        return new CardListPage(rows, first, last, next, screenNum > 1, screenNum, FILE_ERROR, false);
    }

    private CardAnchor anchorForKey(String key) {
        if (key == null) {
            return CardAnchor.empty();
        }
        Map.Entry<String, CardRecord> entry = master.ceilingEntry(key);
        return entry == null ? CardAnchor.empty() : anchor(entry.getValue());
    }

    private static List<CardListRow> blankRows() {
        List<CardListRow> rows = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            rows.add(null);
        }
        return rows;
    }

    private static boolean matches(CardRecord record, CardListFilters filters) {
        if (filters.accountFlag() == CardListInputEditor.FilterFlag.ISVALID
                && !record.cardAcctId().equals(filters.accountId())) {
            return false;
        }
        return filters.cardFlag() != CardListInputEditor.FilterFlag.ISVALID
                || record.cardNumber().equals(filters.cardNumber());
    }

    private static CardListRow row(CardRecord record) {
        return new CardListRow(record.cardNumber(), record.cardAcctId(), record.cardActiveStatus());
    }

    private static CardAnchor anchor(CardRecord record) {
        return new CardAnchor(record.cardNumber(), record.cardAcctId());
    }
}
