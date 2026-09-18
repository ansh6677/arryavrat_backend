package com.aryavart.dairy.dto;

/**
 * The delivery rule as the shop needs it: free at or above
 * {@link #freeDeliveryAbove}, otherwise {@link #deliveryCharge}.
 *
 * Deliberately not the whole settings document — the website has no business
 * knowing who last changed the number or when.
 */
public record DeliveryInfo(double freeDeliveryAbove, double deliveryCharge, String deliveryNote) {
}
