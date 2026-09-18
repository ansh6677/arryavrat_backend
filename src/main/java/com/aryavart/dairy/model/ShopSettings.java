package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Shop-wide rules the farm can change without a deploy.
 *
 * Exactly one of these ever exists — {@link #SINGLETON_ID} is hard-coded as the
 * document id so a save is always an overwrite. A settings collection that can
 * grow a second row eventually gets one, and then half the site reads a
 * different delivery charge than the other half.
 */
@Data
@Document("shop_settings")
public class ShopSettings {

    public static final String SINGLETON_ID = "shop";

    @Id
    private String id = SINGLETON_ID;

    /**
     * Order value at or above which delivery is free. Zero means delivery is
     * always free, which is also what a farm that does not charge should set.
     */
    private double freeDeliveryAbove = 0;

    /** Charged when the order is under {@link #freeDeliveryAbove}. */
    private double deliveryCharge = 0;

    /** Optional line shown under the delivery row in the cart. */
    private String deliveryNote;

    private Instant updatedAt = Instant.now();
    private String updatedBy;
}
