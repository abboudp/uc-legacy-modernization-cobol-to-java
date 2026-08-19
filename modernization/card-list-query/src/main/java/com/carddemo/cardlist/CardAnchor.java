package com.carddemo.cardlist;

public record CardAnchor(String cardNumber, String accountId) {
    public static CardAnchor empty() {
        return new CardAnchor("", "");
    }
}
