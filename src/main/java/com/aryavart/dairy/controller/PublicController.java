package com.aryavart.dairy.controller;

import com.aryavart.dairy.model.Product;
import com.aryavart.dairy.repository.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Endpoints reachable without a login (product listing). */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final ProductRepository productRepository;

    public PublicController(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
    
    @GetMapping("/health")
    public String health() {
        System.out.println("Yes, your cron is running. I am live!");
        return "Yes, your cron is running. I am live!";
    }

    private static int rank(Product p) {
        if (p.isComingSoon()) return 2;
        return p.isAvailable() ? 0 : 1;
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
