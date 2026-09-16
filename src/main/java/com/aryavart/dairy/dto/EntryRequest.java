package com.aryavart.dairy.dto;

import java.time.LocalDate;

/**
 * quantity means packs when packLabel is set, and base units otherwise —
 * 2 with packLabel "Half kg" is two half-kilo packs, 2 without it is 2 kg.
 */
public record EntryRequest(String customerId, String productId, Double quantity, Double rate,
                           LocalDate entryDate, String note, Boolean paid, String paymentMode,
                           /** Pack the customer took; blank for loose quantity. */
                           String packLabel,
                           /** Coupon the customer claimed; blank for a normal entry. */
                           String couponCode) {
}
