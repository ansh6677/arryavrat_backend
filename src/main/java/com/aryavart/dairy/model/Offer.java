package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A percentage discount the farm hands out as a coupon code.
 *
 * The code is what makes it work — nothing is discounted automatically. A
 * customer types it in the cart before sending the WhatsApp order; staff type
 * it in the daily-entry sheet when a customer claims it on their khata. Which
 * of those two a code is allowed for is {@link #scope}, chosen per offer.
 */
@Data
@Document("offers")
public class Offer {

    /** Website cart only — applies to the WhatsApp order the customer sends. */
    public static final String WEBSITE = "WEBSITE";
    /** Khata only — staff apply it while adding daily entries. */
    public static final String KHATA = "KHATA";
    /** Usable in both places. */
    public static final String BOTH = "BOTH";

    @Id
    private String id;

    /** Always stored upper-case — this is the string the customer types. */
    @Indexed(unique = true)
    private String code;

    /** Headline on the site strip, e.g. "10% off everything". */
    private String title;

    /** One line of small print under the headline. Optional. */
    private String description;

    /** 1–90. Taken off the whole order / entry total. */
    private double percentOff;

    /**
     * Order total the code needs before it works. 0 means no minimum.
     *
     * Checked against the total *before* the discount, which is the only order
     * that makes sense: a coupon that drops the bill under its own minimum and
     * so stops applying would flicker on and off as the customer shops.
     */
    private double minOrderAmount;

    /** WEBSITE | KHATA | BOTH */
    private String scope = BOTH;

    /**
     * Switched off, the code stops working immediately but keeps its history —
     * which is why offers are deactivated rather than deleted after a campaign.
     */
    private boolean active = true;

    /** Null on either end means "no limit that way". Both ends inclusive. */
    private LocalDate validFrom;
    private LocalDate validTo;

    /**
     * Whether the sitewide strip advertises this code. Off makes it a private
     * code — it still works, but only for customers who were told it.
     */
    private boolean showOnSite = true;

    /** Rough traction counter — bumped each time the code is actually applied. */
    private long usedCount;

    private Instant createdAt = Instant.now();

    /** Is this code allowed in the place it is being used? */
    public boolean coversScope(String wanted) {
        if (scope == null || BOTH.equals(scope)) return true;
        return scope.equals(wanted);
    }

    /** Active, and inside its date window on the given day. */
    public boolean liveOn(LocalDate day) {
        if (!active) return false;
        if (validFrom != null && day.isBefore(validFrom)) return false;
        return validTo == null || !day.isAfter(validTo);
    }
}
