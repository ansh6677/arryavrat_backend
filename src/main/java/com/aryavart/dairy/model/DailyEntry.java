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
    private double total;       // what the customer owes — discount already taken off

    /**
     * Coupon applied when this entry was written, if any.
     *
     * The discount is baked into {@link #total} rather than worked out later,
     * because everything downstream — outstanding, dashboard, CSV, the PDF —
     * already reads `total` as the single truth. Storing it this way also means
     * ending a campaign never silently re-prices last month's khata.
     */
    /**
     * The pack this entry was sold as, e.g. "Half kg". Null for loose quantity.
     *
     * `quantity` still holds the amount in the product's base unit (a half-kg
     * pack stores 0.5), so the kg/litre summary on the bill keeps adding up;
     * `packCount` is how many packs that was, for the "2 x Half kg" label.
     */
    private String packLabel;
    private double packCount;

    private String couponCode;
    /** Percentage taken off, e.g. 10. */
    private double discountPercent;
    /** quantity x rate, before the discount. Equals total when no coupon was used. */
    private double grossTotal;
    /** grossTotal - total. */
    private double discountAmount;

    /**
     * True for an "old due" — pending khata from before this app (or a past
     * cycle), entered as a lump amount. It has no product; quantity is 1 and
     * rate/total both carry the amount. It stays unpaid and simply raises the
     * customer's outstanding until normal payments clear it.
     */
    private boolean oldDue;
    /** For old dues: the billing month (YYYY-MM) the amount belongs to. */
    private String forPeriod;

    @Indexed
    private LocalDate entryDate;

    private String note;

    /** true when the customer paid on the spot; false = on credit. */
    private boolean paid;

    /** Payment auto-created for a paid entry — removed together with the entry. */
    private String linkedPaymentId;

    private Instant createdAt = Instant.now();
}
