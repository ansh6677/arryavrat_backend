package com.aryavart.dairy.service;

import com.aryavart.dairy.model.ShopSettings;
import com.aryavart.dairy.repository.ShopSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes the one shop-settings document.
 *
 * A farm that has never opened the delivery screen still has to get a sensible
 * answer, so a missing document reads as defaults rather than as an error —
 * nothing is written to Mongo until someone actually saves.
 */
@Service
public class SettingsService {

    private final ShopSettingsRepository repository;

    public SettingsService(ShopSettingsRepository repository) {
        this.repository = repository;
    }

    /** Current settings, or the defaults when nothing has been saved yet. */
    public ShopSettings get() {
        return repository.findById(ShopSettings.SINGLETON_ID).orElseGet(ShopSettings::new);
    }

    /** Tones the strip can be painted in. */
    private static final Set<String> TONES = Set.of("gold", "green", "red");

    /** More than this and the strip takes too long to come back around. */
    private static final int MAX_MESSAGES = 6;

    /**
     * @param freeDeliveryAbove order value at or above which delivery is free
     * @param deliveryCharge    what to charge below that
     */
    public ShopSettings saveDelivery(double freeDeliveryAbove, double deliveryCharge,
                                     String note, String who) {
        if (freeDeliveryAbove < 0 || deliveryCharge < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amounts cannot be negative.");
        }
        if (deliveryCharge > 10000 || freeDeliveryAbove > 1000000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That amount looks like a typo.");
        }

        ShopSettings settings = get();
        settings.setId(ShopSettings.SINGLETON_ID);
        settings.setFreeDeliveryAbove(BillingService.round2(freeDeliveryAbove));
        settings.setDeliveryCharge(BillingService.round2(deliveryCharge));
        settings.setDeliveryNote(note == null || note.isBlank() ? null : note.trim());
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(who);
        return repository.save(settings);
    }

    /**
     * The announcement strip.
     *
     * Blank lines are dropped rather than rejected: the form starts with an
     * empty row, and someone who fills one of three and saves means it.
     */
    public ShopSettings saveBanner(boolean enabled, List<String> messages, String tone, String who) {
        List<String> clean = new ArrayList<>();
        if (messages != null) {
            for (String m : messages) {
                if (m == null || m.isBlank()) continue;
                String text = m.trim();
                if (text.length() > 120) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Keep each line under 120 characters — the strip only has one line to give.");
                }
                clean.add(text);
            }
        }
        if (clean.size() > MAX_MESSAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most " + MAX_MESSAGES + " lines on the strip.");
        }
        if (enabled && clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Write at least one line before switching the strip on.");
        }

        String painted = (tone == null) ? "gold" : tone.trim().toLowerCase();
        if (!TONES.contains(painted)) painted = "gold";

        ShopSettings settings = get();
        settings.setId(ShopSettings.SINGLETON_ID);
        settings.setBannerEnabled(enabled);
        settings.setBannerMessages(clean.isEmpty() ? null : clean);
        settings.setBannerTone(painted);
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(who);
        return repository.save(settings);
    }
}
