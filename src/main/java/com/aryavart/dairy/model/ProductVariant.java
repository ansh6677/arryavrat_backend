package com.aryavart.dairy.model;

import lombok.Data;

/**
 * One sellable pack of a product — "200 g", "Half kg", "1 kg".
 *
 * The price is typed by the farm, never worked out from the per-unit price,
 * because a smaller pack costs proportionally more: paneer at Rs. 420/kg is
 * Rs. 230 for half a kilo, not Rs. 210. That premium has nowhere to live in a
 * single per-unit figure, which is the whole reason packs exist.
 */
@Data
public class ProductVariant {

    /** What the customer taps, e.g. "Half kg". */
    private String label;

    /** How much of the product's base unit one pack holds: 0.2, 0.5, 1. */
    private double quantity;

    /** What one pack costs. */
    private double price;

    /** A pack can sell out on its own while the other sizes stay on the shelf. */
    private boolean available = true;

    /**
     * Price per base unit — the "Rs. 460/kg" hint beside the pack in the
     * management form, and the rate stored on a khata entry so the kg/litre
     * totals on a bill stay honest.
     */
    public double ratePerUnit() {
        return quantity > 0 ? Math.round(price / quantity * 100.0) / 100.0 : 0;
    }
}
