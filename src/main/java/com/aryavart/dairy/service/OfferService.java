package com.aryavart.dairy.service;

import com.aryavart.dairy.model.Offer;
import com.aryavart.dairy.repository.OfferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Every question about a coupon is answered here, so the cart, the khata and
 * the management panel can never disagree about whether a code is usable.
 */
@Service
public class OfferService {

    private final OfferRepository offerRepository;

    public OfferService(OfferRepository offerRepository) {
        this.offerRepository = offerRepository;
    }

    /** Codes are case- and space-insensitive for whoever types them. */
    public static String normalize(String code) {
        if (code == null) return null;
        String trimmed = code.trim().replaceAll("\\s+", "").toUpperCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Percentage off an amount, rounded to paise. */
    public static double discountOn(double amount, Offer offer) {
        if (offer == null || amount <= 0) return 0;
        double pct = Math.min(Math.max(offer.getPercentOff(), 0), 100);
        return BillingService.round2(amount * pct / 100.0);
    }

    /** Offers a customer may actually use in this place today. */
    public List<Offer> liveOffers(String scope) {
        LocalDate today = LocalDate.now();
        return offerRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(o -> o.liveOn(today))
                .filter(o -> o.coversScope(scope))
                .toList();
    }

    /** Live, listable offers — what the cart's offer list shows. */
    public List<Offer> publicOffers() {
        return liveOffers(Offer.WEBSITE).stream()
                .filter(Offer::isShowOnSite)
                .toList();
    }

    /**
     * Looks a typed code up for the given scope.
     *
     * @return null when nothing was typed; never null when a code was given —
     *         an unusable code raises 400 with a message meant to be shown as-is.
     */
    public Offer resolve(String rawCode, String scope) {
        return resolve(rawCode, scope, null);
    }

    /**
     * @param amount order/entry total before discount, for the minimum check.
     *               Null skips that check — used where the total isn't known yet.
     */
    public Offer resolve(String rawCode, String scope, Double amount) {
        String code = normalize(rawCode);
        if (code == null) return null;

        Offer offer = offerRepository.findByCode(code)
                .orElseThrow(() -> bad("That coupon code doesn't exist. Please check the spelling."));

        LocalDate today = LocalDate.now();
        if (!offer.isActive()) {
            throw bad("This coupon is no longer running.");
        }
        if (offer.getValidFrom() != null && today.isBefore(offer.getValidFrom())) {
            throw bad("This coupon starts on " + offer.getValidFrom() + ".");
        }
        if (offer.getValidTo() != null && today.isAfter(offer.getValidTo())) {
            throw bad("This coupon expired on " + offer.getValidTo() + ".");
        }
        if (!offer.coversScope(scope)) {
            throw bad(Offer.KHATA.equals(scope)
                    ? "This coupon is for website orders only."
                    : "This coupon can only be used on your monthly khata.");
        }
        if (offer.getMinOrderAmount() > 0 && amount != null && amount < offer.getMinOrderAmount()) {
            double short_ = BillingService.round2(offer.getMinOrderAmount() - amount);
            throw bad("This coupon needs an order of \u20b9" + trim(offer.getMinOrderAmount())
                    + " or more. Add \u20b9" + trim(short_) + " more to use it.");
        }
        return offer;
    }

    /** Rough traction counter — best effort, never fails the surrounding save. */
    public void markUsed(Offer offer) {
        if (offer == null) return;
        try {
            offer.setUsedCount(offer.getUsedCount() + 1);
            offerRepository.save(offer);
        } catch (RuntimeException ignored) {
            // A counter is not worth failing an order or an entry over.
        }
    }

    /** "500" reads better than "500.0" in a message the customer sees. */
    private static String trim(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(BillingService.round2(v));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
