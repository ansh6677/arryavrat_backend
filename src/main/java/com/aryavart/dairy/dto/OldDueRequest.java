package com.aryavart.dairy.dto;

/**
 * An "old due" — a past month's pending amount added to the customer's khata.
 * month is YYYY-MM; requestId guards against accidental double submits.
 */
public record OldDueRequest(String customerId, Double amount, String month, String note, String requestId) {
}
