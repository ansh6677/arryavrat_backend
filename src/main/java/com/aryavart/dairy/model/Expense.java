package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/** Farm expense (feed, labour, transport, etc.) */
@Data
@Document("expenses")
public class Expense {

    @Id
    private String id;

    private String category;    // Cattle Feed, Labour, Medicine, Transport, ...

    /** How many units were bought (e.g. 5 sacks). Older records default to 1. */
    private double quantity = 1;

    /** Optional unit label shown next to the quantity (sack, litre, hour, ...). */
    private String unit;

    /** Cost of a single unit. amount = quantity x unitAmount. */
    private double unitAmount;

    /** Total spent — always kept in sync with quantity x unitAmount. */
    private double amount;

    @Indexed
    private LocalDate expenseDate;

    private String note;

    private Instant createdAt = Instant.now();
}
