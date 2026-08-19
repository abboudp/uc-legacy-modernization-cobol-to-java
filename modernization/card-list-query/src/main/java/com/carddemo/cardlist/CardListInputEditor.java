package com.carddemo.cardlist;

import java.util.Objects;

public final class CardListInputEditor {
    public enum FilterFlag {
        BLANK, NOT_OK, ISVALID
    }

    public record InputEditResult(boolean inputOk, FilterFlag accountFilter,
                                  FilterFlag cardFilter, String errorMessage,
                                  String accountId, String cardNumber) {
        public CardListFilters filters() {
            return new CardListFilters(accountFilter, accountId, cardFilter, cardNumber);
        }
    }

    public InputEditResult edit(String rawAccountId, String rawCardNumber) {
        FieldResult account = classify(rawAccountId, 11);
        FieldResult card = classify(rawCardNumber, 16);
        String error = null;
        if (account.flag == FilterFlag.NOT_OK) {
            error = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
        } else if (card.flag == FilterFlag.NOT_OK) {
            error = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
        }
        return new InputEditResult(account.flag != FilterFlag.NOT_OK && card.flag != FilterFlag.NOT_OK,
                account.flag, card.flag, error, account.value, card.value);
    }

    private static FieldResult classify(String raw, int width) {
        String zeroes = "0".repeat(width);
        if (raw == null || raw.equals("\0".repeat(width))
                || raw.equals(" ".repeat(width)) || raw.equals(zeroes)) {
            return new FieldResult(FilterFlag.BLANK, zeroes);
        }
        if (raw.length() == width && raw.chars().allMatch(Character::isDigit)) {
            return new FieldResult(FilterFlag.ISVALID, raw);
        }
        return new FieldResult(FilterFlag.NOT_OK, zeroes);
    }

    private record FieldResult(FilterFlag flag, String value) {
        private FieldResult {
            Objects.requireNonNull(flag);
            Objects.requireNonNull(value);
        }
    }
}
