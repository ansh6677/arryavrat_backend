package com.aryavart.dairy.service;

import com.aryavart.dairy.model.ShopSettings;
import com.aryavart.dairy.repository.ShopSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

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
}
