package com.aryavart.dairy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Adds the same basket of products for every date in a range — e.g. 1 litre of
 * milk for five days creates five separate daily entries of 1 litre each.
 */
public record BulkEntryRequest(String customerId, LocalDate from, LocalDate to,
                               Boolean paid, String paymentMode, String note,
                               List<Item> items) {

    public record Item(String productId, Double quantity, Double rate) {
    }
}
