package com.aryavart.dairy.dto;

import java.time.LocalDate;

public record PaymentRequest(String customerId, Double amount, LocalDate paymentDate, String mode, String note,
                             /** YYYY-MM of the old billing cycle this payment clears; null for a normal payment. */
                             String forPeriod) {
}
