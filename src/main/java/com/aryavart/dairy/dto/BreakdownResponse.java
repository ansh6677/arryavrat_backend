package com.aryavart.dairy.dto;

import java.util.List;

/**
 * Who is behind a dashboard figure — the click-through on the cash, online and
 * outstanding cards.
 *
 * The rows always add up to {@link #total}, and {@link #total} always matches
 * the card that was clicked. That is the whole point of the screen: a
 * breakdown that disagrees with the number above it is worse than no breakdown.
 */
public record BreakdownResponse(
        /** CASH | ONLINE | OUTSTANDING */
        String type,
        String title,
        String subtitle,
        double total,
        List<Row> rows) {

    /**
     * @param customerId null for rows that aren't a khata customer — walk-in
     *                   counter sales — so the UI knows not to link them.
     */
    public record Row(String customerId, String customerName, double amount, String detail) {
    }
}
