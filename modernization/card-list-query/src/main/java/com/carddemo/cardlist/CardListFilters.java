package com.carddemo.cardlist;

import java.util.Objects;

public record CardListFilters(CardListInputEditor.FilterFlag accountFlag, String accountId,
                              CardListInputEditor.FilterFlag cardFlag, String cardNumber) {
    public CardListFilters {
        Objects.requireNonNull(accountFlag, "accountFlag");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(cardFlag, "cardFlag");
        Objects.requireNonNull(cardNumber, "cardNumber");
    }

    public static CardListFilters none() {
        return new CardListFilters(CardListInputEditor.FilterFlag.BLANK, "0".repeat(11),
                CardListInputEditor.FilterFlag.BLANK, "0".repeat(16));
    }
}
