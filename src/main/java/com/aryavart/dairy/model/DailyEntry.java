package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/** One day's delivery/purchase entry. */
@Data
@Document("daily_entries")
public class DailyEntry {

    @Id
    private String id;

    @Indexed
    private String customerId;
    private String customerName;

    private String productId;
    private String productName;
    private String unit;

    private double quantity;
    private double rate;        // rate per unit at the time of entry
    private double total;       // quantity * rate

    @Indexed
    private LocalDate entryDate;

    private String note;

    /** true when the customer paid on the spot; false = on credit. */
    private boolean paid;

    /** Payment auto-created for a paid entry — removed together with the entry. */
    private String linkedPaymentId;

    private Instant createdAt = Instant.now();
}
