package com.aryavart.dairy.dto;

/**
 * A customer telling us "I have paid this much by UPI". Amount is optional —
 * blank means the whole outstanding. `ref` is the UTR / reference number from
 * the UPI app, which is what makes the claim checkable against the bank.
 */
public record PaymentClaimRequest(Double amount, String ref, String note, String requestId) {
}
