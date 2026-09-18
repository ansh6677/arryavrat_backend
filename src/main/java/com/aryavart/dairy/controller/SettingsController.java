package com.aryavart.dairy.controller;

import com.aryavart.dairy.dto.BannerInfo;
import com.aryavart.dairy.dto.DeliveryInfo;
import com.aryavart.dairy.model.ShopSettings;
import com.aryavart.dairy.service.SettingsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shop-wide settings the management panel can change.
 *
 * Only the write side lives here; the cart reads the same numbers from
 * /api/public/settings, because a signed-out visitor has to be told the
 * delivery charge before they send their order.
 */
@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ShopSettings get() {
        return settings.get();
    }

    @PutMapping("/delivery")
    public ShopSettings saveDelivery(@RequestBody DeliveryInfo body, Authentication auth) {
        return settings.saveDelivery(
                body.freeDeliveryAbove(),
                body.deliveryCharge(),
                body.deliveryNote(),
                auth != null ? auth.getName() : null);
    }

    @PutMapping("/banner")
    public ShopSettings saveBanner(@RequestBody BannerInfo body, Authentication auth) {
        return settings.saveBanner(
                body.enabled(),
                body.messages(),
                body.tone(),
                auth != null ? auth.getName() : null);
    }
}
