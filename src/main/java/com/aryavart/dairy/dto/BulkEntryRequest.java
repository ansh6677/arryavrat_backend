package com.aryavart.dairy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Adds the same basket of products for every date in a range — e.g. 1 litre of
 * milk for five days creates five separate daily entries of 1 litre each.
 */
public record BulkEntryRequest(String customerId, LocalDate from, LocalDate to,
                               Boolean paid, String paymentMode, String note,
                               List<Item> items,
                               /** Coupon applied to every entry this save creates. Blank = none. */
                               String couponCode,
                               /** Client-generated id for this save tap — repeats are ignored. */
                               String requestId) {
    /**
     * quantity means packs when packLabel is set, and base units otherwise.
     * A pack carries its own price, so rate is ignored whenever packLabel is set.
     */
    public record Item(String productId, Double quantity, Double rate, String packLabel) {
    }
}
