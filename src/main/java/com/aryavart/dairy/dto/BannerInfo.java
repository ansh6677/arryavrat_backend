package com.aryavart.dairy.dto;

import java.util.List;

/**
 * The announcement strip under the site header, as the website needs it.
 *
 * Has nothing to do with offers: whatever the farm types is what scrolls.
 */
public record BannerInfo(boolean enabled, List<String> messages, String tone) {
}
