package com.aryavart.dairy.dto;

import java.time.LocalDate;

public record EntryRequest(String customerId, String productId, Double quantity, Double rate,
                           LocalDate entryDate, String note, Boolean paid, String paymentMode) {
}
