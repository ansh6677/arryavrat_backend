package com.aryavart.dairy.config;

import com.aryavart.dairy.model.Product;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.ProductRepository;
import com.aryavart.dairy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Default product photos shipped with the frontend. */
    private static final Map<String, String> SEED_IMAGES = Map.of(
            "Pure A2 Cow Milk", "assets/photos/prod-milk.jpg",
            "Pure A2 Curd (Dahi)", "assets/photos/prod-curd.jpg",
            "Fresh A2 Paneer", "assets/photos/prod-paneer.jpg",
            "A2 Desi Ghee (Bilona)", "assets/photos/prod-ghee.jpg",
            "Fresh Buttermilk (Chaach)", "assets/photos/prod-buttermilk.jpg");

    /**
     * Old asset paths migrated to the current photo set.
     *
     * `prod-buttermilk.png` was in fact a 5120x5120 JPEG carrying a .png name,
     * which made the frontend treat it as a transparent cut-out and letterbox it
     * on a pale tile while every other card was a full-bleed photo. It has been
     * re-cut onto a dark backdrop and saved with the correct extension, so any
     * database still holding the old path is pointed at the new file here.
     *
     * `CURD3.jpg` was the matka-curd photograph dropped straight into
     * `src/assets/photos` and pinned on the curd product by hand. That folder is
     * generated output — the file was overwritten on the next pipeline run — so
     * the shot now lives in `photo-masters/curd-matka.png` and is built, cropped
     * and graded, into the standard `prod-curd.jpg` slot.
     */
    private static final Map<String, String> LEGACY_IMAGES = Map.of(
            "assets/products/milk.png", "assets/photos/prod-milk.jpg",
            "assets/products/ghee.png", "assets/photos/prod-ghee.jpg",
            "assets/products/buttermilk.png", "assets/photos/prod-buttermilk.jpg",
            "assets/products/paneer1.png", "assets/photos/prod-paneer.jpg",
            "assets/photos/prod-buttermilk.png", "assets/photos/prod-buttermilk.jpg",
            "assets/photos/hero-paneer.jpg", "assets/photos/prod-paneer.jpg",
            "assets/photos/CURD3.jpg", "assets/photos/prod-curd.jpg");

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.super-admin-id}")
    private String superAdminId;

    @Value("${app.seed.super-admin-pass}")
    private String superAdminPass;

    public DataSeeder(UserRepository userRepository, ProductRepository productRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedSuperAdmin();
        seedProducts();
        syncProductImages();
    }

    /** The .env SUPER_ADMIN_ID/PASS is the source of truth — synced on every startup. */
    private void seedSuperAdmin() {
        User admin = userRepository.findByPhone(superAdminId).orElseGet(User::new);
        boolean isNew = admin.getId() == null;
        admin.setPhone(superAdminId);
        if (admin.getName() == null || admin.getName().isBlank()) {
            admin.setName("Super Admin");
        }
        admin.setRole("ADMIN");
        admin.setActive(true);
        admin.setPassword(passwordEncoder.encode(superAdminPass));
        admin.setSuperAdmin(true);
        userRepository.save(admin);
        log.info("Super admin {} (login id: {})", isNew ? "created" : "synced from .env", superAdminId);
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            return;
        }
        productRepository.saveAll(List.of(
                ordered(1, product("Pure A2 Cow Milk", "Milk", "Litre", 90,
                        "Farm-fresh A2 milk from healthy desi cows, delivered within 2 hours of milking.")),
                ordered(2, product("Pure A2 Curd (Dahi)", "Curd", "Kg", 120,
                        "Thick, traditional-set curd made from pure A2 milk — just like homemade.")),
                ordered(3, product("Fresh A2 Paneer", "Paneer", "Kg", 450,
                        "Soft, fresh paneer made daily from the finest farm milk.")),
                ordered(4, product("A2 Desi Ghee (Bilona)", "Ghee", "Kg", 2200,
                        "Slow-churned bilona ghee with rich aroma, golden colour and authentic taste.")),
                ordered(5, product("Fresh Buttermilk (Chaach)", "Buttermilk", "Litre", 40,
                        "Light, refreshing buttermilk churned daily from farm-fresh curd."))));
        // Upcoming products the farm is preparing — admins can edit or remove these.
        productRepository.saveAll(List.of(
                comingSoon("Farm Fresh Mushrooms", "Mushroom", "Kg", 320,
                        "Button mushrooms grown in our own climate-controlled shed. Launching soon."),
                comingSoon("Stone-Ground Spices", "Spices", "Kg", 480,
                        "Turmeric, chilli and coriander, sun-dried and stone-ground in small batches. Launching soon.")));

        log.info("Seeded {} sample products (update prices from /management)", productRepository.count());
    }

    /** Fills default photos where blank and migrates legacy asset paths on old databases. */
    private void syncProductImages() {
        for (Product p : productRepository.findAll()) {
            String img = p.getImageUrl();
            if ((img == null || img.isBlank()) && SEED_IMAGES.containsKey(p.getName())) {
                p.setImageUrl(SEED_IMAGES.get(p.getName()));
                productRepository.save(p);
            } else if (img != null && LEGACY_IMAGES.containsKey(img)) {
                p.setImageUrl(LEGACY_IMAGES.get(img));
                productRepository.save(p);
            }
        }
    }

    private Product ordered(int sortOrder, Product p) {
        p.setSortOrder(sortOrder);
        return p;
    }

    /** Teaser product: visible on the site with a "Coming soon" badge. */
    private Product comingSoon(String name, String category, String unit, double price, String description) {
        Product p = product(name, category, unit, price, description);
        p.setAvailable(false);
        p.setComingSoon(true);
        return p;
    }

    private Product product(String name, String category, String unit, double price, String description) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setUnit(unit);
        p.setPrice(price);
        p.setDescription(description);
        p.setImageUrl(SEED_IMAGES.get(name));
        p.setAvailable(true);
        return p;
    }
}
