package com.aryavart.dairy.dto;

import java.time.LocalDate;

public record PaymentRequest(String customerId, Double amount, LocalDate paymentDate, String mode, String note) {
}
