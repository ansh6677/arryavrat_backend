package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A walk-in / counter sale — the occasional offline customer who is not in the
 * customer register. Paid on the spot, so it never touches anyone's outstanding;
 * it simply adds to the day's sales.
 */
@Data
@Document("extra_sales")
public class ExtraSale {

    @Id
    private String id;

    /** Optional walk-in name; blank shows as "Walk-in customer". */
    private String customerName;

    /** Set when the sale is of a listed product; free-text items leave it null. */
    private String productId;

    private String productName;

    private String unit;

    private double quantity = 1;

    private double rate;

    /** Always quantity x rate. */
    private double total;

    @Indexed
    private LocalDate saleDate;

    /** Cash / UPI — walk-ins settle immediately. */
    private String paymentMode;

    private String note;

    private Instant createdAt = Instant.now();
}
