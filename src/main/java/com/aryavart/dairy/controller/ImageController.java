package com.aryavart.dairy.controller;

import com.aryavart.dairy.service.ImageStoreService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Photo upload for the management panel, and the public URL those photos are
 * served from.
 *
 * The two halves sit in one class on purpose — an upload is only useful if the
 * URL it hands back resolves, and keeping the pair together means the path can
 * never drift apart. The read side lives under /api/public so the shop can
 * show a product photo to a signed-out visitor; only the write side is behind
 * the admin role, which the security config already enforces by path.
 */
@RestController
public class ImageController {

    private final ImageStoreService images;

    public ImageController(ImageStoreService images) {
        this.images = images;
    }

    /**
     * @return {"id": "...", "url": "/public/images/..."} — the url is relative
     *         to the API root so the same database works against localhost and
     *         the deployed backend without rewriting every stored product.
     */
    @PostMapping(value = "/api/admin/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        String id = images.store(file);
        return Map.of("id", id, "url", "/public/images/" + id);
    }

    @DeleteMapping("/api/admin/images/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        images.delete(id);
        return Map.of("status", "deleted");
    }

    /**
     * Cached hard and for a long time: an id is only ever minted for one set of
     * bytes, so a stored photo can never change under a browser that kept it.
     */
    @GetMapping("/api/public/images/{id}")
    public ResponseEntity<byte[]> serve(@PathVariable String id) {
        ImageStoreService.Loaded found = images.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.contentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(found.bytes());
    }
}
