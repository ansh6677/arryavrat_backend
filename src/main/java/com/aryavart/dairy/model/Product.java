package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("products")
public class Product {

    @Id
    private String id;

    private String name;
    private String category;     // e.g. Milk, Curd, Paneer, Ghee, Buttermilk
    private String description;
    private String unit;         // Litre, Kg, Gram, Piece, Packet
    private double price;        // price per unit (INR)
    private String imageUrl;
    private boolean available = true;

    /** Teaser products (e.g. Mushroom, Spices) shown on the site before launch. */
    private boolean comingSoon = false;

    /** Display position on the website and slider — lower comes first. */
    private int sortOrder = 100;

    /**
     * Sellable pack sizes. Null or empty means the product is sold by the unit
     * at {@link #price}, exactly as everything did before packs existed — which
     * is why adding this field changes nothing for milk, curd or anything else
     * until packs are actually set on it.
     */
    private java.util.List<ProductVariant> variants;

    /** True when this product is sold in packs rather than by loose quantity. */
    public boolean packed() {
        return variants != null && !variants.isEmpty();
    }

    private Instant createdAt = Instant.now();
}
