package com.carddemo.cardlist;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record CardListPage(List<CardListRow> rows, CardAnchor firstAnchor, CardAnchor lastAnchor,
                           boolean nextPageExists, boolean previousPageExists, int screenNum,
                           String errorMessage, boolean noRecordsFound) {
    public CardListPage {
        rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }
}
