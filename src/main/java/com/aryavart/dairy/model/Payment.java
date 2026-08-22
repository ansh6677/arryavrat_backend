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
    private String mode;        // Cash, UPI, Bank, Other
    private String note;

    private Instant createdAt = Instant.now();
}
