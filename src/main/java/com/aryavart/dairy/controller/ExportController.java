package com.aryavart.dairy.controller;

import com.aryavart.dairy.dto.BillResponse;
import com.aryavart.dairy.dto.StatsResponse;
import com.aryavart.dairy.model.DailyEntry;
import com.aryavart.dairy.model.Expense;
import com.aryavart.dairy.model.ExtraSale;
import com.aryavart.dairy.model.Payment;
import com.aryavart.dairy.model.Product;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.DailyEntryRepository;
import com.aryavart.dairy.repository.ExpenseRepository;
import com.aryavart.dairy.repository.ExtraSaleRepository;
import com.aryavart.dairy.repository.PaymentRepository;
import com.aryavart.dairy.repository.ProductRepository;
import com.aryavart.dairy.repository.UserRepository;
import com.aryavart.dairy.service.BillingService;
import com.aryavart.dairy.service.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV downloads for every register the farm keeps. Files open cleanly in
 * Excel/Google Sheets (UTF-8 BOM, CRLF); GETs, so view-only staff can export.
 */
@RestController
@RequestMapping("/api/admin/export")
public class ExportController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final DailyEntryRepository entryRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final ExtraSaleRepository extraSaleRepository;
    private final BillingService billingService;
    private final StatsService statsService;

    public ExportController(UserRepository userRepository, ProductRepository productRepository,
                            DailyEntryRepository entryRepository, PaymentRepository paymentRepository,
                            ExpenseRepository expenseRepository, ExtraSaleRepository extraSaleRepository,
                            BillingService billingService, StatsService statsService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.entryRepository = entryRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.extraSaleRepository = extraSaleRepository;
        this.billingService = billingService;
        this.statsService = statsService;
    }

    // ------------------------------ helpers ------------------------------

    private static String esc(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static void add(List<String> out, Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(esc(cells[i]));
        }
        out.add(sb.toString());
    }

    private ResponseEntity<byte[]> csv(String filename, List<String> lines) {
        // BOM so Excel detects UTF-8 (Hindi names, the rupee sign)
        String body = "\uFEFF" + String.join("\r\n", lines) + "\r\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDate orMonthStart(LocalDate d) {
        return d != null ? d : LocalDate.now().withDayOfMonth(1);
    }

    private static LocalDate orToday(LocalDate d) {
        return d != null ? d : LocalDate.now();
    }

    // ------------------------------ registers ------------------------------

    @GetMapping("/customers.csv")
    public ResponseEntity<byte[]> customers() {
        List<String> out = new ArrayList<>();
        add(out, "Name", "Phone", "Email", "Address", "Active", "Source", "Joined");
        for (User c : userRepository.findByRoleOrderByNameAsc("CUSTOMER")) {
            add(out, c.getName(), c.getPhone(), c.getEmail(), c.getAddress(),
                    c.isActive() ? "Yes" : "No",
                    "PAGE".equals(c.getSignupSource()) ? "Website" : "ADF",
                    c.getCreatedAt());
        }
        return csv("customers.csv", out);
    }

    @GetMapping("/products.csv")
    public ResponseEntity<byte[]> products() {
        List<String> out = new ArrayList<>();
        add(out, "Order", "Name", "Category", "Unit", "Price", "Status");
        for (Product p : productRepository.findAllByOrderByCategoryAscNameAsc()) {
            String status = p.isComingSoon() ? "Coming soon" : (p.isAvailable() ? "Available" : "Not available");
            add(out, p.getSortOrder(), p.getName(), p.getCategory(), p.getUnit(), p.getPrice(), status);
        }
        return csv("products.csv", out);
    }

    @GetMapping("/entries.csv")
    public ResponseEntity<byte[]> entries(@RequestParam(required = false) String customerId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = orMonthStart(from);
        LocalDate t = orToday(to);
        List<DailyEntry> list = (customerId != null && !customerId.isBlank())
                ? entryRepository.findForCustomerInRange(customerId, f, t)
                : entryRepository.findInRange(f, t);
        List<String> out = new ArrayList<>();
        add(out, "Date", "Customer", "Product", "Quantity", "Unit", "Rate", "Total", "Paid", "Note");
        for (DailyEntry e : list) {
            add(out, e.getEntryDate(), e.getCustomerName(), e.getProductName(),
                    e.getQuantity(), e.getUnit(), e.getRate(), e.getTotal(),
                    e.isPaid() ? "Yes" : "No", e.getNote());
        }
        return csv("daily-entries_" + f + "_to_" + t + ".csv", out);
    }

    @GetMapping("/payments.csv")
    public ResponseEntity<byte[]> payments(@RequestParam(required = false) String customerId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = orMonthStart(from);
        LocalDate t = orToday(to);
        List<Payment> list = (customerId != null && !customerId.isBlank())
                ? paymentRepository.findByCustomerIdOrderByPaymentDateDesc(customerId).stream()
                    .filter(p -> p.getPaymentDate() != null
                            && !p.getPaymentDate().isBefore(f) && !p.getPaymentDate().isAfter(t))
                    .toList()
                : paymentRepository.findInRange(f, t);
        List<String> out = new ArrayList<>();
        add(out, "Date", "Customer", "Amount", "Mode", "For period", "Note");
        for (Payment p : list) {
            add(out, p.getPaymentDate(), p.getCustomerName(), p.getAmount(), p.getMode(),
                    p.getForPeriod() == null ? "" : p.getForPeriod(), p.getNote());
        }
        return csv("payments_" + f + "_to_" + t + ".csv", out);
    }

    @GetMapping("/expenses.csv")
    public ResponseEntity<byte[]> expenses(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = orMonthStart(from);
        LocalDate t = orToday(to);
        List<String> out = new ArrayList<>();
        add(out, "Date", "Category", "Quantity", "Unit", "Rate", "Amount", "Note");
        double total = 0;
        for (Expense x : expenseRepository.findInRange(f, t)) {
            add(out, x.getExpenseDate(), x.getCategory(),
                    x.getQuantity() > 0 ? x.getQuantity() : 1, x.getUnit(),
                    x.getUnitAmount() > 0 ? x.getUnitAmount() : x.getAmount(),
                    x.getAmount(), x.getNote());
            total += x.getAmount();
        }
        add(out, "", "", "", "", "Total", BillingService.round2(total), "");
        return csv("expenses_" + f + "_to_" + t + ".csv", out);
    }

    @GetMapping("/extra-sales.csv")
    public ResponseEntity<byte[]> extraSales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = orMonthStart(from);
        LocalDate t = orToday(to);
        List<String> out = new ArrayList<>();
        add(out, "Date", "Customer", "Item", "Quantity", "Unit", "Rate", "Total", "Mode", "Note");
        double total = 0;
        for (ExtraSale x : extraSaleRepository.findInRange(f, t)) {
            add(out, x.getSaleDate(), x.getCustomerName(), x.getProductName(),
                    x.getQuantity(), x.getUnit(), x.getRate(), x.getTotal(),
                    x.getPaymentMode(), x.getNote());
            total += x.getTotal();
        }
        add(out, "", "", "", "", "", "Total", BillingService.round2(total), "", "");
        return csv("extra-sales_" + f + "_to_" + t + ".csv", out);
    }

    /** One customer's full statement — purchases, payments and the balance story. */
    @GetMapping("/customer-bill.csv")
    public ResponseEntity<byte[]> customerBill(@RequestParam String customerId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        BillResponse bill = billingService.getBill(customerId, orMonthStart(from), orToday(to));
        List<String> out = new ArrayList<>();
        add(out, "Customer", bill.customerName());
        add(out, "Phone", bill.phone());
        add(out, "Period", bill.from() + " to " + bill.to());
        add(out, "Previous balance", bill.previousBalance());
        out.add("");
        add(out, "PURCHASES");
        add(out, "Date", "Product", "Quantity", "Unit", "Rate", "Total", "Paid");
        for (DailyEntry e : bill.entries()) {
            add(out, e.getEntryDate(), e.getProductName(), e.getQuantity(), e.getUnit(),
                    e.getRate(), e.getTotal(), e.isPaid() ? "Yes" : "No");
        }
        out.add("");
        add(out, "PAYMENTS");
        add(out, "Date", "Mode", "Amount", "Note");
        for (Payment p : bill.payments()) {
            add(out, p.getPaymentDate(), p.getMode(), p.getAmount(), p.getNote());
        }
        out.add("");
        add(out, "Period purchases", bill.periodTotal());
        add(out, "Period payments", bill.periodPaid());
        add(out, "Total purchases (all time)", bill.lifetimePurchases());
        add(out, "Total paid (all time)", bill.lifetimePaid());
        add(out, "Outstanding", bill.outstanding());
        String safe = bill.customerName().replaceAll("[^A-Za-z0-9]+", "-");
        return csv("bill_" + safe + "_" + bill.from() + "_to_" + bill.to() + ".csv", out);
    }

    /** Day-wise month report: regular sales, walk-in sales, expenses and net. */
    @GetMapping("/month-report.csv")
    public ResponseEntity<byte[]> monthReport(@RequestParam(required = false) String month) {
        YearMonth ym = (month == null || month.isBlank()) ? null : YearMonth.parse(month.trim());
        StatsResponse stats = statsService.overview(ym);
        List<String> out = new ArrayList<>();
        add(out, "Month", stats.monthLabel());
        out.add("");
        add(out, "Date", "Sales (incl. walk-in)", "Walk-in sales", "Expenses", "Net");
        for (StatsResponse.DayPoint d : stats.days()) {
            add(out, d.date(), d.sales(), d.extra(), d.expenses(),
                    BillingService.round2(d.sales() - d.expenses()));
        }
        out.add("");
        add(out, "Month sales", stats.monthSales());
        add(out, "Month walk-in sales", stats.monthExtraSales());
        add(out, "Month expenses", stats.monthExpenses());
        add(out, "Month profit", stats.monthProfit());
        return csv("month-report_" + stats.month() + ".csv", out);
    }
}
