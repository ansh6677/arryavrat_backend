package com.aryavart.dairy.dto;

/**
 * The answer to "is this code any good, and what does it save me?".
 *
 * A wrong code is not an HTTP error — it is a normal answer with valid=false
 * and a message the cart shows under the input, so a typo never looks like the
 * site broke.
 */
public record CouponPreview(
        boolean valid,
        String code,
        String title,
        String description,
        double percentOff,
        /** Order total the code needs; 0 when there is none. */
        double minOrderAmount,
        /** The amount the discount was worked out on. */
        double amount,
        double discount,
        /** amount - discount. */
        double payable,
        /** Why it was rejected, or the win to show when it was accepted. */
        String message) {

    public static CouponPreview rejected(String code, String message) {
        return new CouponPreview(false, code, null, null, 0, 0, 0, 0, 0, message);
    }
}
