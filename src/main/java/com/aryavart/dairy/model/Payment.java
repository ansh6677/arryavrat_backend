package com.aryavart.dairy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** Money the farm has actually verified — counts towards the khata. */
    public static final String CONFIRMED = "CONFIRMED";
    /**
     * A customer tapped "Yes, paid" on the UPI sheet. It is NOT counted in the
     * khata until an admin confirms it, otherwise anyone could clear their own
     * outstanding with one tap.
     */
    public static final String PENDING = "PENDING";

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

    /**
     * CONFIRMED or PENDING. Payments written before this field existed have it
     * null, which {@link #isConfirmed()} reads as CONFIRMED — the old rows were
     * all staff-entered, so nothing in an existing khata shifts.
     */
    @Indexed
    private String status;

    /** UPI reference / UTR the customer typed while claiming the payment. */
    private String claimedRef;

    /** Who confirmed a PENDING claim, and when. */
    private String confirmedBy;
    private Instant confirmedAt;

    private Instant createdAt = Instant.now();

    @JsonIgnore
    public boolean isConfirmed() {
        return status == null || CONFIRMED.equals(status);
    }

    @JsonIgnore
    public boolean isPending() {
        return PENDING.equals(status);
    }
}
