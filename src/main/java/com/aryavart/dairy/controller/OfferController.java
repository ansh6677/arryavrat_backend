package com.aryavart.dairy.controller;

import com.aryavart.dairy.model.Offer;
import com.aryavart.dairy.repository.OfferRepository;
import com.aryavart.dairy.service.OfferService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Offers & coupon codes for the management panel.
 *
 * Sits under /api/admin, so SecurityConfig already applies: view-only staff can
 * read the list, only full admins can create, edit or delete.
 */
@RestController
@RequestMapping("/api/admin/offers")
public class OfferController {

    private final OfferRepository offerRepository;
    private final OfferService offerService;

    public OfferController(OfferRepository offerRepository, OfferService offerService) {
        this.offerRepository = offerRepository;
        this.offerService = offerService;
    }

    @GetMapping
    public List<Offer> all() {
        return offerRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Just the codes that work right now, for the scope asked about. The daily
     * entry sheet uses this to show staff which codes are running today.
     */
    @GetMapping("/live")
    public List<Offer> live(@RequestParam(required = false) String scope) {
        return offerService.liveOffers(scope == null || scope.isBlank() ? Offer.KHATA : normalizeScope(scope));
    }

    @PostMapping
    public Offer add(@RequestBody Offer req) {
        Offer offer = new Offer();
        apply(offer, req, true);
        return offerRepository.save(offer);
    }

    @PutMapping("/{id}")
    public Offer update(@PathVariable String id, @RequestBody Offer req) {
        Offer offer = offerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        apply(offer, req, false);
        return offerRepository.save(offer);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        if (!offerRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found");
        }
        offerRepository.deleteById(id);
        return Map.of("status", "deleted");
    }

    /** Validation lives here so create and edit can never drift apart. */
    private void apply(Offer target, Offer req, boolean isNew) {
        String code = OfferService.normalize(req.getCode());
        if (code == null) throw bad("A coupon code is required");
        if (code.length() < 3 || code.length() > 20) {
            throw bad("The coupon code must be between 3 and 20 characters");
        }
        if (!code.matches("[A-Z0-9]+")) {
            throw bad("The coupon code can only use letters and numbers — no spaces or symbols");
        }
        // A second offer on the same code would make the customer's discount a
        // coin toss, so the code stays unique across the whole collection.
        if (isNew || !code.equals(target.getCode())) {
            if (offerRepository.existsByCode(code)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Another offer already uses this code");
            }
        }

        double pct = req.getPercentOff();
        if (pct <= 0) throw bad("The discount must be greater than 0%");
        if (pct > 90) throw bad("The discount cannot be more than 90%");

        if (req.getMinOrderAmount() < 0) throw bad("The minimum order amount cannot be negative");

        if (req.getValidFrom() != null && req.getValidTo() != null
                && req.getValidTo().isBefore(req.getValidFrom())) {
            throw bad("The end date cannot be before the start date");
        }

        String title = (req.getTitle() == null || req.getTitle().isBlank())
                ? trimZeros(pct) + "% off everything"
                : req.getTitle().trim();

        target.setCode(code);
        target.setTitle(title);
        target.setDescription(req.getDescription() == null || req.getDescription().isBlank()
                ? null : req.getDescription().trim());
        target.setPercentOff(round2(pct));
        target.setMinOrderAmount(round2(Math.max(0, req.getMinOrderAmount())));
        target.setScope(normalizeScope(req.getScope()));
        target.setActive(req.isActive());
        target.setShowOnSite(req.isShowOnSite());
        target.setValidFrom(req.getValidFrom());
        target.setValidTo(req.getValidTo());
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return Offer.BOTH;
        String s = scope.trim().toUpperCase();
        if (!s.equals(Offer.WEBSITE) && !s.equals(Offer.KHATA) && !s.equals(Offer.BOTH)) {
            throw bad("Where the coupon works must be WEBSITE, KHATA or BOTH");
        }
        return s;
    }

    /** "10.0" reads badly in a headline; "10" does. */
    private static String trimZeros(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(round2(v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
