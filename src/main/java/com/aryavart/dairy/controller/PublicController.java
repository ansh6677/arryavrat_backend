package com.aryavart.dairy.controller;

import com.aryavart.dairy.dto.CouponPreview;
import com.aryavart.dairy.dto.BannerInfo;
import com.aryavart.dairy.dto.DeliveryInfo;
import com.aryavart.dairy.model.Offer;
import com.aryavart.dairy.model.Product;
import com.aryavart.dairy.repository.ProductRepository;
import com.aryavart.dairy.service.BillingService;
import com.aryavart.dairy.service.OfferService;
import com.aryavart.dairy.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Endpoints reachable without a login (product listing, live offers). */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    private final ProductRepository productRepository;
    private final OfferService offerService;
    private final SettingsService settingsService;

    public PublicController(ProductRepository productRepository, OfferService offerService,
                            SettingsService settingsService) {
        this.productRepository = productRepository;
        this.offerService = offerService;
        this.settingsService = settingsService;
    }

    /**
     * The delivery rule, for the cart. Public because the charge has to appear
     * in the order summary before anyone signs in — most customers never do.
     */
    @GetMapping("/settings")
    public DeliveryInfo settings() {
        var s = settingsService.get();
        return new DeliveryInfo(s.getFreeDeliveryAbove(), s.getDeliveryCharge(), s.getDeliveryNote());
    }

    /**
     * The announcement strip under the header. Separate from /settings on
     * purpose: every page asks for this, only the cart asks for delivery.
     */
    @GetMapping("/banner")
    public BannerInfo banner() {
        var s = settingsService.get();
        List<String> lines = s.getBannerMessages() == null ? List.of() : s.getBannerMessages();
        return new BannerInfo(s.isBannerEnabled() && !lines.isEmpty(), lines, s.getBannerTone());
    }

    /**
     * Every product is returned — sold-out and upcoming items included. The site
     * shows them with a clear status badge instead of hiding them, so customers
     * can still see the full range and request what they want.
     * Order: available first, then unavailable, then coming soon.
     */
    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) String category) {
        List<Product> products = productRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .sorted(Comparator.comparingInt(PublicController::rank)
                        .thenComparingInt(Product::getSortOrder)
                        .thenComparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (category == null || category.isBlank() || "All".equalsIgnoreCase(category)) {
            return products;
        }
        return products.stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .toList();
    }
    
    /** Keep-alive ping for the hosting cron — a spun-down free dyno wakes on this. */
    @GetMapping("/health")
    public String health() {
        log.debug("Health ping received");
        return "OK";
    }

    private static int rank(Product p) {
        if (p.isComingSoon()) return 2;
        return p.isAvailable() ? 0 : 1;
    }

    // ------------------------------ Offers ------------------------------

    /**
     * Live, listable coupons — what the cart's offer list
     * announces. Private codes (showOnSite off) still work, they just aren't
     * listed here.
     */
    @GetMapping("/offers")
    public List<Offer> offers() {
        return offerService.publicOffers();
    }

    /**
     * "Does this code work, and what does it take off my ₹640?"
     *
     * A bad code answers 200 with valid=false, not an HTTP error — a typo in
     * the cart should read as a hint under the input, not as a broken site.
     */
    @GetMapping("/offers/validate")
    public CouponPreview validate(@RequestParam String code,
                                  @RequestParam(required = false) Double amount) {
        String typed = OfferService.normalize(code);
        if (typed == null) {
            return CouponPreview.rejected(code, "Please enter a coupon code.");
        }
        Offer offer;
        try {
            offer = offerService.resolve(typed, Offer.WEBSITE,
                    (amount == null || amount < 0) ? null : amount);
        } catch (ResponseStatusException e) {
            return CouponPreview.rejected(typed, e.getReason());
        }
        double base = (amount == null || amount < 0) ? 0 : BillingService.round2(amount);
        double discount = OfferService.discountOn(base, offer);
        return new CouponPreview(true, offer.getCode(), offer.getTitle(), offer.getDescription(),
                offer.getPercentOff(), offer.getMinOrderAmount(), base, discount, BillingService.round2(base - discount),
                "Coupon applied — you save ₹" + discount + ".");
    }

    /**
     * Bumped when a customer actually sends their WhatsApp order with a code
     * attached. Orders live in WhatsApp rather than in this database, so this
     * counter is the only signal the farm gets about which campaign is landing.
     */
    @PostMapping("/offers/{code}/used")
    public Map<String, String> markUsed(@PathVariable String code) {
        try {
            offerService.markUsed(offerService.resolve(code, Offer.WEBSITE));
        } catch (ResponseStatusException ignored) {
            // Counting is best-effort; a stale code here must not break checkout.
        }
        return Map.of("status", "ok");
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
