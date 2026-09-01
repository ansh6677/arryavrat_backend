package com.aryavart.dairy.controller;

import com.aryavart.dairy.dto.BillResponse;
import com.aryavart.dairy.dto.CustomerRequest;
import com.aryavart.dairy.dto.BulkEntryRequest;
import com.aryavart.dairy.dto.DayDetail;
import com.aryavart.dairy.dto.EntryRequest;
import com.aryavart.dairy.dto.OldDueRequest;
import com.aryavart.dairy.dto.PaymentRequest;
import com.aryavart.dairy.dto.StaffRequest;
import com.aryavart.dairy.dto.StatsResponse;
import com.aryavart.dairy.model.DailyEntry;
import com.aryavart.dairy.model.Expense;
import com.aryavart.dairy.model.ExtraSale;
import com.aryavart.dairy.model.LoginEvent;
import com.aryavart.dairy.model.Payment;
import com.aryavart.dairy.model.ProcessedRequest;
import com.aryavart.dairy.model.Product;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.DailyEntryRepository;
import com.aryavart.dairy.repository.ProcessedRequestRepository;
import com.aryavart.dairy.repository.ExpenseRepository;
import com.aryavart.dairy.repository.ExtraSaleRepository;
import com.aryavart.dairy.repository.LoginEventRepository;
import com.aryavart.dairy.repository.PaymentRepository;
import com.aryavart.dairy.repository.ProductRepository;
import com.aryavart.dairy.repository.UserRepository;
import com.aryavart.dairy.service.BillingService;
import com.aryavart.dairy.service.StatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /management panel ke saare endpoints.
 * SecurityConfig: GET → ADMIN + VIEWER, write → ADMIN only, /staff → ADMIN only.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final DailyEntryRepository entryRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final ExtraSaleRepository extraSaleRepository;
    private final LoginEventRepository loginEventRepository;
    private final ProcessedRequestRepository processedRequestRepository;
    private final BillingService billingService;
    private final StatsService statsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.super-admin-id}")
    private String superAdminId;

    public AdminController(UserRepository userRepository, ProductRepository productRepository,
                           DailyEntryRepository entryRepository, PaymentRepository paymentRepository,
                           ExpenseRepository expenseRepository, ExtraSaleRepository extraSaleRepository,
                           LoginEventRepository loginEventRepository,
                           ProcessedRequestRepository processedRequestRepository,
                           BillingService billingService,
                           StatsService statsService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.processedRequestRepository = processedRequestRepository;
        this.extraSaleRepository = extraSaleRepository;
        this.loginEventRepository = loginEventRepository;
        this.productRepository = productRepository;
        this.entryRepository = entryRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.billingService = billingService;
        this.statsService = statsService;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------ Customers ------------------------------

    @GetMapping("/customers")
    public List<User> customers() {
        return userRepository.findByRoleOrderByNameAsc("CUSTOMER");
    }

    @PostMapping("/customers")
    public User addCustomer(@RequestBody CustomerRequest req) {
        if (req.name() == null || req.name().isBlank()) throw badRequest("Customer name is required");
        if (req.phone() == null || req.phone().isBlank()) throw badRequest("Customer phone is required");
        String phone = req.phone().trim();
        if (userRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this phone already exists");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setPhone(phone);
        user.setEmail(req.email());
        user.setAddress(req.address());
        String rawPassword = (req.password() == null || req.password().isBlank()) ? phone : req.password();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("CUSTOMER");
        user.setActive(req.active() == null || req.active());
        user.setSignupSource("ADF");
        user.setPreferredProductIds(req.preferredProductIds());
        user.setPreferredQuantities(req.preferredQuantities());
        return userRepository.save(user);
    }

    @PutMapping("/customers/{id}")
    public User updateCustomer(@PathVariable String id, @RequestBody CustomerRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> notFound("Customer not found"));
        // The customer route must never touch the super admin — or any staff
        // account. Without this, passing a staff id here could rename them,
        // reset their password or deactivate them through the back door.
        guardSuperAdmin(user);
        if (!"CUSTOMER".equals(user.getRole())) throw notFound("Customer not found");
        if (req.name() != null && !req.name().isBlank()) user.setName(req.name().trim());
        if (req.phone() != null && !req.phone().isBlank() && !req.phone().trim().equals(user.getPhone())) {
            String phone = req.phone().trim();
            if (userRepository.existsByPhone(phone)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Another user already has this phone");
            }
            user.setPhone(phone);
        }
        if (req.email() != null) user.setEmail(req.email());
        if (req.address() != null) user.setAddress(req.address());
        if (req.password() != null && !req.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        if (req.active() != null) user.setActive(req.active());
        if (req.preferredProductIds() != null) user.setPreferredProductIds(req.preferredProductIds());
        if (req.preferredQuantities() != null) user.setPreferredQuantities(req.preferredQuantities());
        return userRepository.save(user);
    }

    /** Any customer's date-range bill (entries + payments + outstanding). */
    @GetMapping("/customers/{id}/bill")
    public BillResponse customerBill(@PathVariable String id,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return billingService.getBill(id, from, to);
    }

    // ---------------------------- Daily entries ----------------------------

    @PostMapping("/entries")
    public DailyEntry addEntry(@RequestBody EntryRequest req) {
        if (req.customerId() == null || req.customerId().isBlank()) throw badRequest("Please select a customer");
        if (req.productId() == null || req.productId().isBlank()) throw badRequest("Please select a product");
        if (req.quantity() == null || req.quantity() <= 0) throw badRequest("Quantity must be greater than 0");

        User customer = userRepository.findById(req.customerId())
                .orElseThrow(() -> notFound("Customer not found"));
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> notFound("Product not found"));

        double rate = (req.rate() != null && req.rate() > 0) ? req.rate() : product.getPrice();

        DailyEntry entry = new DailyEntry();
        entry.setCustomerId(customer.getId());
        entry.setCustomerName(customer.getName());
        entry.setProductId(product.getId());
        entry.setProductName(product.getName());
        entry.setUnit(product.getUnit());
        entry.setQuantity(req.quantity());
        entry.setRate(rate);
        entry.setTotal(BillingService.round2(req.quantity() * rate));
        entry.setEntryDate(req.entryDate() != null ? req.entryDate() : LocalDate.now());
        entry.setNote(req.note());
        entry = entryRepository.save(entry);

        // "Paid" entry: record the matching payment immediately so the ledger,
        // dashboard and outstanding all stay consistent automatically.
        if (Boolean.TRUE.equals(req.paid())) {
            Payment payment = new Payment();
            payment.setCustomerId(customer.getId());
            payment.setCustomerName(customer.getName());
            payment.setAmount(entry.getTotal());
            payment.setPaymentDate(entry.getEntryDate());
            payment.setMode((req.paymentMode() == null || req.paymentMode().isBlank()) ? "Cash" : req.paymentMode());
            payment.setNote("Paid with entry — " + product.getName());
            payment = paymentRepository.save(payment);

            entry.setPaid(true);
            entry.setLinkedPaymentId(payment.getId());
            entry = entryRepository.save(entry);
        }
        return entry;
    }

    /**
     * Adds the selected products for every date in a range. Choosing milk at 1
     * litre from the 1st to the 5th creates five entries of 1 litre each.
     */
    @PostMapping("/entries/bulk")
    public Map<String, Object> addEntriesBulk(@RequestBody BulkEntryRequest req) {
        if (req.customerId() == null || req.customerId().isBlank()) throw badRequest("Please select a customer");
        if (req.items() == null || req.items().isEmpty()) throw badRequest("Please select at least one product");

        LocalDate from = (req.from() != null) ? req.from() : LocalDate.now();
        LocalDate to = (req.to() != null) ? req.to() : from;
        if (to.isBefore(from)) throw badRequest("The end date cannot be before the start date");
        long dayCount = ChronoUnit.DAYS.between(from, to) + 1;
        if (dayCount > 92) throw badRequest("Please choose a date range of 92 days or less");

        User customer = userRepository.findById(req.customerId())
                .orElseThrow(() -> notFound("Customer not found"));

        // Double-tap / retry protection: every save from the UI carries a unique
        // requestId. The first request claims the id (unique _id insert); an
        // accidental repeat of the same tap is answered without writing anything,
        // so one tap can never become two sets of entries.
        boolean lockTaken = false;
        if (req.requestId() != null && !req.requestId().isBlank()) {
            try {
                processedRequestRepository.insert(new ProcessedRequest(req.requestId()));
                lockTaken = true;
            } catch (DuplicateKeyException e) {
                return Map.of(
                        "created", 0,
                        "days", 0L,
                        "totalAmount", 0.0,
                        "duplicate", true);
            }
        }

        try {
            int created = 0;
            double totalAmount = 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                for (BulkEntryRequest.Item item : req.items()) {
                    if (item == null || item.productId() == null || item.productId().isBlank()) continue;
                    double qty = (item.quantity() != null) ? item.quantity() : 0;
                    if (qty <= 0) continue;
                    Product product = productRepository.findById(item.productId())
                            .orElseThrow(() -> notFound("Product not found"));
                    DailyEntry entry = saveEntry(customer, product, qty, item.rate(), d,
                            req.note(), Boolean.TRUE.equals(req.paid()), req.paymentMode());
                    created++;
                    totalAmount += entry.getTotal();
                }
            }
            if (created == 0) throw badRequest("Please set a quantity greater than 0 for at least one product");

            return Map.of(
                    "created", created,
                    "days", dayCount,
                    "totalAmount", BillingService.round2(totalAmount),
                    "duplicate", false);
        } catch (RuntimeException e) {
            // The save failed — release the id so the user can retry cleanly.
            if (lockTaken) processedRequestRepository.deleteById(req.requestId());
            throw e;
        }
    }

    /** Shared by the single and bulk entry endpoints. */
    private DailyEntry saveEntry(User customer, Product product, double quantity, Double rateOverride,
                                 LocalDate date, String note, boolean paid, String paymentMode) {
        double rate = (rateOverride != null && rateOverride > 0) ? rateOverride : product.getPrice();

        DailyEntry entry = new DailyEntry();
        entry.setCustomerId(customer.getId());
        entry.setCustomerName(customer.getName());
        entry.setProductId(product.getId());
        entry.setProductName(product.getName());
        entry.setUnit(product.getUnit());
        entry.setQuantity(quantity);
        entry.setRate(rate);
        entry.setTotal(BillingService.round2(quantity * rate));
        entry.setEntryDate(date != null ? date : LocalDate.now());
        entry.setNote(note);
        entry = entryRepository.save(entry);

        if (paid) {
            Payment payment = new Payment();
            payment.setCustomerId(customer.getId());
            payment.setCustomerName(customer.getName());
            payment.setAmount(entry.getTotal());
            payment.setPaymentDate(entry.getEntryDate());
            payment.setMode((paymentMode == null || paymentMode.isBlank()) ? "Cash" : paymentMode);
            payment.setNote("Paid with entry — " + product.getName());
            payment = paymentRepository.save(payment);

            entry.setPaid(true);
            entry.setLinkedPaymentId(payment.getId());
            entry = entryRepository.save(entry);
        }
        return entry;
    }

    @GetMapping("/entries")
    public List<DailyEntry> entries(@RequestParam(required = false) String customerId,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = (from != null) ? from : LocalDate.now();
        LocalDate t = (to != null) ? to : f;
        if (customerId == null || customerId.isBlank()) {
            return entryRepository
                    .findInRange(f, t);
        }
        return entryRepository
                .findForCustomerInRange(
                        customerId, f, t);
    }

    @DeleteMapping("/entries/{id}")
    public Map<String, String> deleteEntry(@PathVariable String id) {
        DailyEntry entry = entryRepository.findById(id).orElseThrow(() -> notFound("Entry not found"));
        // A paid entry carries its auto-payment with it, so totals stay correct.
        if (entry.getLinkedPaymentId() != null && paymentRepository.existsById(entry.getLinkedPaymentId())) {
            paymentRepository.deleteById(entry.getLinkedPaymentId());
        }
        entryRepository.deleteById(id);
        return Map.of("status", "deleted");
    }

    // ------------------------------ Payments ------------------------------

    @PostMapping("/payments")
    public Payment addPayment(@RequestBody PaymentRequest req) {
        if (req.customerId() == null || req.customerId().isBlank()) throw badRequest("Please select a customer");
        if (req.amount() == null || req.amount() <= 0) throw badRequest("Amount must be greater than 0");

        User customer = userRepository.findById(req.customerId())
                .orElseThrow(() -> notFound("Customer not found"));

        // "Old payment": money received today that clears an earlier billing
        // month. The month is validated and kept on the record so every bill,
        // table and export can show which cycle it belongs to.
        String forPeriod = null;
        if (req.forPeriod() != null && !req.forPeriod().isBlank()) {
            YearMonth cycle;
            try {
                cycle = YearMonth.parse(req.forPeriod().trim());
            } catch (Exception e) {
                throw badRequest("The old payment month must look like 2026-07");
            }
            if (cycle.isAfter(YearMonth.now())) {
                throw badRequest("The old payment month cannot be in the future");
            }
            forPeriod = cycle.toString();
        }

        Payment payment = new Payment();
        payment.setCustomerId(customer.getId());
        payment.setCustomerName(customer.getName());
        payment.setAmount(BillingService.round2(req.amount()));
        payment.setPaymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now());
        String defaultMode = (forPeriod != null) ? "Old dues" : "Cash";
        payment.setMode((req.mode() == null || req.mode().isBlank()) ? defaultMode : req.mode());
        payment.setNote(req.note());
        payment.setForPeriod(forPeriod);
        return paymentRepository.save(payment);
    }

    /**
     * "Old due" — pending khata of a past month entered as a lump amount
     * (e.g. January's Rs. 1500 for paneer still unpaid). It is stored as an
     * UNPAID entry dated to that month, so it raises the outstanding, rolls
     * into "previous balance" on later bills, and is cleared the normal way —
     * by recording payments. It is never marked paid by itself.
     */
    @PostMapping("/entries/old-due")
    public DailyEntry addOldDue(@RequestBody OldDueRequest req) {
        if (req.customerId() == null || req.customerId().isBlank()) throw badRequest("Please select a customer");
        if (req.amount() == null || req.amount() <= 0) throw badRequest("Amount must be greater than 0");
        if (req.month() == null || req.month().isBlank()) throw badRequest("Please choose the month the due belongs to");

        YearMonth cycle;
        try {
            cycle = YearMonth.parse(req.month().trim());
        } catch (Exception e) {
            throw badRequest("The old due month must look like 2026-01");
        }
        if (cycle.isAfter(YearMonth.now())) throw badRequest("The old due month cannot be in the future");

        User customer = userRepository.findById(req.customerId())
                .orElseThrow(() -> notFound("Customer not found"));

        // Same double-tap protection as bulk entries: one tap, one due.
        boolean lockTaken = false;
        if (req.requestId() != null && !req.requestId().isBlank()) {
            try {
                processedRequestRepository.insert(new ProcessedRequest(req.requestId()));
                lockTaken = true;
            } catch (DuplicateKeyException e) {
                return entryRepository.findFirstByCustomerIdAndOldDueTrueAndForPeriodOrderByCreatedAtDesc(
                        customer.getId(), cycle.toString())
                        .orElseThrow(() -> badRequest("This old due was already saved"));
            }
        }

        try {
            DailyEntry due = new DailyEntry();
            due.setCustomerId(customer.getId());
            due.setCustomerName(customer.getName());
            due.setProductId(null);
            due.setProductName("Old due — " + monthTitle(cycle));
            due.setUnit("");
            due.setQuantity(1);
            due.setRate(BillingService.round2(req.amount()));
            due.setTotal(BillingService.round2(req.amount()));
            due.setEntryDate(cycle.atEndOfMonth());
            due.setNote(req.note());
            due.setPaid(false);
            due.setOldDue(true);
            due.setForPeriod(cycle.toString());
            return entryRepository.save(due);
        } catch (RuntimeException e) {
            if (lockTaken) processedRequestRepository.deleteById(req.requestId());
            throw e;
        }
    }

    /** "2026-01" -> "Jan 2026" for the old-due line label. */
    private static String monthTitle(YearMonth ym) {
        return ym.format(java.time.format.DateTimeFormatter.ofPattern("MMM uuuu", java.util.Locale.ENGLISH));
    }

    @GetMapping("/payments")
    public List<Payment> payments(@RequestParam(required = false) String customerId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (customerId != null && !customerId.isBlank()) {
            return paymentRepository.findByCustomerIdOrderByPaymentDateDesc(customerId);
        }
        LocalDate f = (from != null) ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = (to != null) ? to : LocalDate.now();
        return paymentRepository
                .findInRange(f, t);
    }

    @DeleteMapping("/payments/{id}")
    public Map<String, String> deletePayment(@PathVariable String id) {
        if (!paymentRepository.existsById(id)) throw notFound("Payment not found");
        // If this payment was auto-created by a "paid" entry, flip that entry back to credit.
        entryRepository.findFirstByLinkedPaymentId(id).ifPresent(e -> {
            e.setPaid(false);
            e.setLinkedPaymentId(null);
            entryRepository.save(e);
        });
        paymentRepository.deleteById(id);
        return Map.of("status", "deleted");
    }

    // ------------------------------ Expenses ------------------------------

    @PostMapping("/expenses")
    public Expense addExpense(@RequestBody Expense req) {
        double qty = (req.getQuantity() > 0) ? req.getQuantity() : 1;
        // Newer clients send quantity + rate; a plain total still works.
        double unitAmount = (req.getUnitAmount() > 0)
                ? req.getUnitAmount()
                : (req.getAmount() > 0 ? req.getAmount() / qty : 0);
        double total = BillingService.round2(qty * unitAmount);
        if (total <= 0) throw badRequest("Enter a quantity and a rate greater than 0");

        Expense expense = new Expense();
        expense.setCategory((req.getCategory() == null || req.getCategory().isBlank()) ? "Other" : req.getCategory());
        expense.setQuantity(BillingService.round2(qty));
        expense.setUnit((req.getUnit() == null || req.getUnit().isBlank()) ? null : req.getUnit().trim());
        expense.setUnitAmount(BillingService.round2(unitAmount));
        expense.setAmount(total);
        expense.setExpenseDate(req.getExpenseDate() != null ? req.getExpenseDate() : LocalDate.now());
        expense.setNote(req.getNote());
        return expenseRepository.save(expense);
    }

    @GetMapping("/expenses")
    public List<Expense> expenses(@RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = (from != null) ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = (to != null) ? to : LocalDate.now();
        return expenseRepository
                .findInRange(f, t);
    }

    @DeleteMapping("/expenses/{id}")
    public Map<String, String> deleteExpense(@PathVariable String id) {
        if (!expenseRepository.existsById(id)) throw notFound("Expense not found");
        expenseRepository.deleteById(id);
        return Map.of("status", "deleted");
    }

    // ------------------------------ Products ------------------------------

    @GetMapping("/products")
    public List<Product> products() {
        return productRepository.findAllByOrderByCategoryAscNameAsc();
    }

    @PostMapping("/products")
    public Product addProduct(@RequestBody Product product) {
        validateProduct(product);
        product.setId(null);
        return productRepository.save(product);
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable String id, @RequestBody Product req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> notFound("Product not found"));
        validateProduct(req);
        product.setName(req.getName());
        product.setCategory(req.getCategory());
        product.setDescription(req.getDescription());
        product.setUnit(req.getUnit());
        product.setPrice(req.getPrice());
        product.setImageUrl(req.getImageUrl());
        product.setAvailable(req.isAvailable());
        product.setComingSoon(req.isComingSoon());
        product.setSortOrder(req.getSortOrder());
        return productRepository.save(product);
    }

    @DeleteMapping("/products/{id}")
    public Map<String, String> deleteProduct(@PathVariable String id) {
        if (!productRepository.existsById(id)) throw notFound("Product not found");
        productRepository.deleteById(id);
        return Map.of("status", "deleted");
    }

    private void validateProduct(Product p) {
        if (p.getName() == null || p.getName().isBlank()) throw badRequest("Product name is required");
        if (p.getUnit() == null || p.getUnit().isBlank()) throw badRequest("Product unit is required");
        if (p.getPrice() <= 0) throw badRequest("Product price must be greater than 0");
    }

    // ---------------------- Staff (full-admin only) -----------------------

    @GetMapping("/staff")
    public List<User> staff() {
        return userRepository.findByRoleInOrderByNameAsc(List.of("ADMIN", "VIEWER"));
    }

    @PostMapping("/staff")
    public User addStaff(@RequestBody StaffRequest req) {
        if (req.name() == null || req.name().isBlank()) throw badRequest("Staff name is required");
        if (req.loginId() == null || req.loginId().isBlank()) throw badRequest("Login id is required");
        String role = normalizeRole(req.role());
        String loginId = req.loginId().trim();
        if (userRepository.existsByPhone(loginId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This login id is already taken");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setPhone(loginId);
        String rawPassword = (req.password() == null || req.password().isBlank()) ? loginId : req.password();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setActive(req.active() == null || req.active());
        return userRepository.save(user);
    }

    @PutMapping("/staff/{id}")
    public User updateStaff(@PathVariable String id, @RequestBody StaffRequest req) {
        User user = staffById(id);
        guardSuperAdmin(user);
        if (req.name() != null && !req.name().isBlank()) user.setName(req.name().trim());
        if (req.loginId() != null && !req.loginId().isBlank() && !req.loginId().trim().equals(user.getPhone())) {
            String loginId = req.loginId().trim();
            if (userRepository.existsByPhone(loginId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This login id is already taken");
            }
            user.setPhone(loginId);
        }
        if (req.role() != null && !req.role().isBlank()) user.setRole(normalizeRole(req.role()));
        if (req.password() != null && !req.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        if (req.active() != null) user.setActive(req.active());
        return userRepository.save(user);
    }

    @DeleteMapping("/staff/{id}")
    public Map<String, String> deleteStaff(@PathVariable String id) {
        User user = staffById(id);
        guardSuperAdmin(user);
        userRepository.deleteById(id);
        return Map.of("status", "deleted");
    }

    private User staffById(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> notFound("Staff member not found"));
        if (!"ADMIN".equals(user.getRole()) && !"VIEWER".equals(user.getRole())) {
            throw badRequest("This user is not a staff account");
        }
        return user;
    }

    /**
     * The super admin is untouchable from every endpoint — matched by the
     * persistent flag AND the current .env id, so even renamed or historical
     * copies of the account stay locked.
     */
    private void guardSuperAdmin(User user) {
        boolean isSuper = user.isSuperAdmin()
                || (user.getPhone() != null && user.getPhone().equals(superAdminId));
        if (isSuper) {
            throw badRequest("This administrator account is system-managed and cannot be edited or deleted");
        }
    }

    private String normalizeRole(String role) {
        if (role == null) throw badRequest("Role is required (ADMIN or VIEWER)");
        String r = role.trim().toUpperCase();
        if (!r.equals("ADMIN") && !r.equals("VIEWER")) {
            throw badRequest("Role must be ADMIN (full access) or VIEWER (view only)");
        }
        return r;
    }

    // ------------------------------- Stats --------------------------------

    // ------------------------------ Sign-in activity ------------------------------

    /** Last sign-in per side + the recent feed — the Login Management page. */
    @GetMapping("/logins")
    public Map<String, Object> loginActivity() {
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("lastManagement", loginEventRepository.findFirstBySideOrderByAtDesc("MANAGEMENT").orElse(null));
        out.put("lastCustomer", loginEventRepository.findFirstBySideOrderByAtDesc("CUSTOMER").orElse(null));
        out.put("recent", loginEventRepository.findTop12ByOrderByAtDesc());
        return out;
    }

    // ------------------------------ Extra sales (walk-in counter) ------------------------------

    /** Records an offline counter sale. Paid on the spot — no customer ledger involved. */
    @PostMapping("/extra-sales")
    public ExtraSale addExtraSale(@RequestBody ExtraSale req) {
        double qty = (req.getQuantity() > 0) ? req.getQuantity() : 0;
        if (qty <= 0) throw badRequest("Quantity must be greater than 0");

        String name = (req.getProductName() == null) ? null : req.getProductName().trim();
        double rate = req.getRate();
        String unit = (req.getUnit() == null || req.getUnit().isBlank()) ? null : req.getUnit().trim();

        // Picking a listed product fills in whatever the request left blank.
        if (req.getProductId() != null && !req.getProductId().isBlank()) {
            Product product = productRepository.findById(req.getProductId())
                    .orElseThrow(() -> notFound("Product not found"));
            if (name == null || name.isBlank()) name = product.getName();
            if (rate <= 0) rate = product.getPrice();
            if (unit == null) unit = product.getUnit();
        }
        if (name == null || name.isBlank()) throw badRequest("Please pick a product or type the item name");
        if (rate <= 0) throw badRequest("Rate must be greater than 0");

        ExtraSale sale = new ExtraSale();
        sale.setCustomerName((req.getCustomerName() == null || req.getCustomerName().isBlank())
                ? "Walk-in customer" : req.getCustomerName().trim());
        sale.setProductId((req.getProductId() == null || req.getProductId().isBlank()) ? null : req.getProductId());
        sale.setProductName(name);
        sale.setUnit(unit);
        sale.setQuantity(BillingService.round2(qty));
        sale.setRate(BillingService.round2(rate));
        sale.setTotal(BillingService.round2(qty * rate));
        sale.setSaleDate(req.getSaleDate() != null ? req.getSaleDate() : LocalDate.now());
        sale.setPaymentMode((req.getPaymentMode() == null || req.getPaymentMode().isBlank()) ? "Cash" : req.getPaymentMode());
        sale.setNote(req.getNote());
        return extraSaleRepository.save(sale);
    }

    @GetMapping("/extra-sales")
    public List<ExtraSale> extraSales(@RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = (from != null) ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = (to != null) ? to : LocalDate.now();
        return extraSaleRepository.findInRange(f, t);
    }

    /** The page's own mini-dashboard: today / this month / all time. */
    @GetMapping("/extra-sales/summary")
    public Map<String, Object> extraSalesSummary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        List<ExtraSale> all = extraSaleRepository.findAll();
        double todayTotal = 0, monthTotal = 0, allTotal = 0;
        long monthCount = 0;
        for (ExtraSale x : all) {
            allTotal += x.getTotal();
            LocalDate d = x.getSaleDate();
            if (d == null) continue;
            if (!d.isBefore(monthStart) && !d.isAfter(today)) { monthTotal += x.getTotal(); monthCount++; }
            if (today.equals(d)) todayTotal += x.getTotal();
        }
        return Map.of(
                "todayTotal", BillingService.round2(todayTotal),
                "monthTotal", BillingService.round2(monthTotal),
                "allTimeTotal", BillingService.round2(allTotal),
                "monthCount", monthCount,
                "allCount", all.size());
    }

    @DeleteMapping("/extra-sales/{id}")
    public Map<String, String> deleteExtraSale(@PathVariable String id) {
        if (!extraSaleRepository.existsById(id)) throw notFound("Sale not found");
        extraSaleRepository.deleteById(id);
        return Map.of("status", "deleted");
    }

    @GetMapping("/stats/overview")
    public StatsResponse stats(@RequestParam(required = false) String month) {
        YearMonth selected = null;
        if (month != null && !month.isBlank()) {
            try {
                selected = YearMonth.parse(month.trim());
            } catch (Exception ex) {
                throw badRequest("Invalid month. Please use the format YYYY-MM.");
            }
        }
        return statsService.overview(selected);
    }

    /** Sales and expenses for a single day — opens when a chart bar is clicked. */
    @GetMapping("/stats/day")
    public DayDetail statsDay(@RequestParam
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return statsService.day(date);
    }

    // ------------------------------ Helpers -------------------------------

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
