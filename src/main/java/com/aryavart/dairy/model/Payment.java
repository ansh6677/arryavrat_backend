package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/** A payment received from the customer. */
@Data
@Document("payments")
public class Payment {

    @Id
    private String id;

    @Indexed
    private String customerId;
    private String customerName;

    private double amount;
    private LocalDate paymentDate;
    private String mode;        // Cash, UPI, Bank, Other, Old dues
    private String note;

    /**
     * Set only for an "old payment" — the billing month (YYYY-MM) this money
     * clears, e.g. "2026-07" when July's dues are paid late. Null for normal
     * payments. paymentDate stays the day the money was actually received.
     */
    private String forPeriod;

    private Instant createdAt = Instant.now();
}
