package com.aryavart.dairy.service;

import com.aryavart.dairy.dto.BreakdownResponse;
import com.aryavart.dairy.dto.DayDetail;
import com.aryavart.dairy.dto.StatsResponse;
import com.aryavart.dairy.dto.StatsResponse.DatePoint;
import com.aryavart.dairy.dto.StatsResponse.DayPoint;
import com.aryavart.dairy.dto.StatsResponse.MonthOption;
import com.aryavart.dairy.dto.StatsResponse.ProductSale;
import com.aryavart.dairy.model.DailyEntry;
import com.aryavart.dairy.model.Expense;
import com.aryavart.dairy.model.ExtraSale;
import com.aryavart.dairy.model.Payment;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.DailyEntryRepository;
import com.aryavart.dairy.repository.ExpenseRepository;
import com.aryavart.dairy.repository.ExtraSaleRepository;
import com.aryavart.dairy.repository.PaymentRepository;
import com.aryavart.dairy.repository.ProductRepository;
import com.aryavart.dairy.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter FULL_DAY_LABEL = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final DailyEntryRepository entryRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final ExtraSaleRepository extraSaleRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public StatsService(DailyEntryRepository entryRepository, PaymentRepository paymentRepository,
                        ExpenseRepository expenseRepository, ExtraSaleRepository extraSaleRepository,
                        UserRepository userRepository,
                        ProductRepository productRepository) {
        this.entryRepository = entryRepository;
        this.extraSaleRepository = extraSaleRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    private static final class Agg {
        String name;
        String unit;
        double todayQty;
        double todayAmt;
        double monthQty;
        double monthAmt;
    }

    /**
     * Dashboard figures for one month. Today's cards always reflect the real
     * today; everything labelled "month" follows the selected month so the
     * dropdown can walk back through the last 12 months.
     */
    public StatsResponse overview(YearMonth selected) {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth month = (selected != null) ? selected : thisMonth;

        LocalDate monthStart = month.atDay(1);
        // For the running month stop at today; past months run to their last day.
        LocalDate monthEnd = month.equals(thisMonth) ? today : month.atEndOfMonth();

        List<DailyEntry> allEntries = entryRepository.findAll();
        // Confirmed money only — a customer's unverified "I paid by UPI" claim
        // must not show up as collected cash in the overview.
        List<Payment> allPayments = paymentRepository.findAll().stream()
                .filter(Payment::isConfirmed).toList();
        List<Expense> allExpenses = expenseRepository.findAll();
        List<ExtraSale> allExtra = extraSaleRepository.findAll();

        // Walk-in sales count as sales AND as money already received, so the
        // outstanding figure (sales - paid) stays untouched by them.
        double totalExtraSales = allExtra.stream().mapToDouble(ExtraSale::getTotal).sum();
        double totalSales = allEntries.stream().mapToDouble(DailyEntry::getTotal).sum() + totalExtraSales;
        double totalPaid = allPayments.stream().mapToDouble(Payment::getAmount).sum() + totalExtraSales;
        double totalExpenses = allExpenses.stream().mapToDouble(Expense::getAmount).sum();

        double todayExtraSales = 0;
        double monthExtraSales = 0;
        // Cash vs online, for today / the selected month / all time. Counter
        // sales are folded in here too: the farm cares how much cash is in the
        // drawer, not which table the row happens to live in.
        double todayCashIn = 0, todayOnlineIn = 0;
        double monthCashIn = 0, monthOnlineIn = 0;
        double totalCashIn = 0, totalOnlineIn = 0;

        for (ExtraSale x : allExtra) {
            LocalDate d = x.getSaleDate();
            if (d == null) continue;
            if (today.equals(d)) todayExtraSales += x.getTotal();
            if (!d.isBefore(monthStart) && !d.isAfter(monthEnd)) monthExtraSales += x.getTotal();

            boolean cash = isCash(x.getPaymentMode());
            if (cash) totalCashIn += x.getTotal(); else totalOnlineIn += x.getTotal();
            if (today.equals(d)) {
                if (cash) todayCashIn += x.getTotal(); else todayOnlineIn += x.getTotal();
            }
            if (!d.isBefore(monthStart) && !d.isAfter(monthEnd)) {
                if (cash) monthCashIn += x.getTotal(); else monthOnlineIn += x.getTotal();
            }
        }

        for (Payment pay : allPayments) {
            LocalDate d = pay.getPaymentDate();
            if (d == null) continue;
            boolean cash = isCash(pay.getMode());
            if (cash) totalCashIn += pay.getAmount(); else totalOnlineIn += pay.getAmount();
            if (today.equals(d)) {
                if (cash) todayCashIn += pay.getAmount(); else todayOnlineIn += pay.getAmount();
            }
            if (!d.isBefore(monthStart) && !d.isAfter(monthEnd)) {
                if (cash) monthCashIn += pay.getAmount(); else monthOnlineIn += pay.getAmount();
            }
        }

        double todaySales = 0;
        double monthSales = 0;
        long todayEntryCount = 0;

        Map<String, Agg> byProduct = new LinkedHashMap<>();

        for (DailyEntry e : allEntries) {
            LocalDate d = e.getEntryDate();
            if (d == null) continue;
            boolean inMonth = !d.isBefore(monthStart) && !d.isAfter(monthEnd);
            boolean isToday = today.equals(d);
            if (isToday) {
                todaySales += e.getTotal();
                todayEntryCount++;
            }
            if (inMonth) monthSales += e.getTotal();
            if (!inMonth && !isToday) continue;

            String key = e.getProductId() != null ? e.getProductId() : String.valueOf(e.getProductName());
            Agg agg = byProduct.computeIfAbsent(key, k -> new Agg());
            agg.name = e.getProductName();
            agg.unit = e.getUnit();
            if (inMonth) {
                agg.monthQty += e.getQuantity();
                agg.monthAmt += e.getTotal();
            }
            if (isToday) {
                agg.todayQty += e.getQuantity();
                agg.todayAmt += e.getTotal();
            }
        }

        for (ExtraSale x : allExtra) {
            LocalDate d = x.getSaleDate();
            if (d == null) continue;
            boolean inMonth = !d.isBefore(monthStart) && !d.isAfter(monthEnd);
            boolean isToday = today.equals(d);
            if (!inMonth && !isToday) continue;
            String key = x.getProductId() != null ? x.getProductId() : "x:" + x.getProductName();
            Agg agg = byProduct.computeIfAbsent(key, k -> new Agg());
            if (agg.name == null) agg.name = x.getProductName();
            if (agg.unit == null) agg.unit = x.getUnit();
            if (inMonth) {
                agg.monthQty += x.getQuantity();
                agg.monthAmt += x.getTotal();
            }
            if (isToday) {
                agg.todayQty += x.getQuantity();
                agg.todayAmt += x.getTotal();
            }
        }

        List<ProductSale> productSales = byProduct.entrySet().stream()
                .map(en -> new ProductSale(
                        en.getKey(),
                        en.getValue().name,
                        en.getValue().unit,
                        BillingService.round2(en.getValue().todayQty),
                        BillingService.round2(en.getValue().todayAmt),
                        BillingService.round2(en.getValue().monthQty),
                        BillingService.round2(en.getValue().monthAmt)))
                .sorted(Comparator.comparingDouble(ProductSale::monthAmount).reversed())
                .toList();

        double todayExpenses = allExpenses.stream()
                .filter(x -> today.equals(x.getExpenseDate()))
                .mapToDouble(Expense::getAmount).sum();
        double monthExpenses = allExpenses.stream()
                .filter(x -> x.getExpenseDate() != null
                        && !x.getExpenseDate().isBefore(monthStart)
                        && !x.getExpenseDate().isAfter(monthEnd))
                .mapToDouble(Expense::getAmount).sum();

        Map<LocalDate, Double> salesByDate = allEntries.stream()
                .filter(e -> e.getEntryDate() != null)
                .collect(Collectors.groupingBy(DailyEntry::getEntryDate,
                        Collectors.summingDouble(DailyEntry::getTotal)));
        Map<LocalDate, Double> expenseByDate = allExpenses.stream()
                .filter(x -> x.getExpenseDate() != null)
                .collect(Collectors.groupingBy(Expense::getExpenseDate,
                        Collectors.summingDouble(Expense::getAmount)));
        Map<LocalDate, Double> extraByDate = allExtra.stream()
                .filter(x -> x.getSaleDate() != null)
                .collect(Collectors.groupingBy(ExtraSale::getSaleDate,
                        Collectors.summingDouble(ExtraSale::getTotal)));

        List<DayPoint> days = new ArrayList<>();
        for (LocalDate d = monthStart; !d.isAfter(monthEnd); d = d.plusDays(1)) {
            double extra = extraByDate.getOrDefault(d, 0.0);
            days.add(new DayPoint(
                    d.toString(),
                    DAY_LABEL.format(d),
                    BillingService.round2(salesByDate.getOrDefault(d, 0.0) + extra),
                    BillingService.round2(expenseByDate.getOrDefault(d, 0.0)),
                    BillingService.round2(extra)));
        }

        Map<YearMonth, Double> byMonth = allEntries.stream()
                .filter(e -> e.getEntryDate() != null)
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getEntryDate()),
                        Collectors.summingDouble(DailyEntry::getTotal)));
        for (ExtraSale x : allExtra) {
            if (x.getSaleDate() == null) continue;
            byMonth.merge(YearMonth.from(x.getSaleDate()), x.getTotal(), Double::sum);
        }

        List<DatePoint> monthly = new ArrayList<>();
        List<MonthOption> months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = thisMonth.minusMonths(i);
            monthly.add(new DatePoint(MONTH_LABEL.format(ym.atDay(1)),
                    BillingService.round2(byMonth.getOrDefault(ym, 0.0))));
            months.add(0, new MonthOption(ym.toString(), MONTH_LONG.format(ym.atDay(1))));
        }

        return new StatsResponse(
                month.toString(),
                MONTH_LONG.format(monthStart),
                BillingService.round2(todaySales + todayExtraSales),
                BillingService.round2(monthSales + monthExtraSales),
                BillingService.round2(totalSales),
                BillingService.round2(todayExtraSales),
                BillingService.round2(monthExtraSales),
                BillingService.round2(totalExtraSales),
                BillingService.round2(totalPaid),
                BillingService.round2(todayCashIn),
                BillingService.round2(todayOnlineIn),
                BillingService.round2(monthCashIn),
                BillingService.round2(monthOnlineIn),
                BillingService.round2(totalCashIn),
                BillingService.round2(totalOnlineIn),
                BillingService.round2(totalSales - totalPaid),
                BillingService.round2(todayExpenses),
                BillingService.round2(monthExpenses),
                BillingService.round2(totalExpenses),
                BillingService.round2(monthSales + monthExtraSales - monthExpenses),
                userRepository.countByRole("CUSTOMER"),
                productRepository.count(),
                todayEntryCount,
                productSales,
                days,
                monthly,
                months);
    }

    /**
     * Cash is the default for anything unlabelled — every row written before
     * modes were recorded was a hand-to-hand payment, so reading a blank as
     * cash keeps the historical split honest rather than inventing UPI.
     */
    static boolean isCash(String mode) {
        if (mode == null || mode.isBlank()) return true;
        String m = mode.trim().toLowerCase();
        return m.equals("cash") || m.equals("offline");
    }

    /**
     * Who makes up one dashboard figure.
     *
     * Deliberately rebuilt from the same repositories and the same rules as
     * {@link #overview}: cash counts counter sales, a blank payment mode reads
     * as cash, and only confirmed payments count. Sharing the rules is what
     * keeps the rows adding up to the card.
     */
    public BreakdownResponse breakdown(String type, YearMonth selected) {
        String kind = (type == null ? "" : type.trim().toUpperCase());
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth month = (selected != null) ? selected : thisMonth;
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.equals(thisMonth) ? today : month.atEndOfMonth();
        String monthName = month.atDay(1).format(MONTH_LONG);

        Map<String, String> names = userRepository.findByRoleOrderByNameAsc("CUSTOMER").stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        return switch (kind) {
            case "OUTSTANDING" -> outstanding(names);
            case "TODAY_SALES" -> sales(names, today, today, "TODAY_SALES",
                    "Today's sales", FULL_DAY_LABEL.format(today));
            case "MONTH_SALES" -> sales(names, monthStart, monthEnd, "MONTH_SALES",
                    monthName + " sales", "Khata entries and counter sales");
            case "WALKIN" -> walkin(monthStart, monthEnd, monthName);
            case "EXPENSES" -> expenses(monthStart, monthEnd, monthName);
            case "PROFIT" -> profit(monthStart, monthEnd, monthName);
            case "CUSTOMERS" -> customers(names);
            default -> collected("CASH".equals(kind), names, monthStart, monthEnd, monthName);
        };
    }

    /** Entries minus confirmed payments, per customer — all time. */
    private BreakdownResponse outstanding(Map<String, String> names) {
        // Counter sales are absent on purpose: they are paid on the spot and
        // cancel out of the dashboard's own outstanding figure too.
        Map<String, Double> owed = new java.util.HashMap<>();
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (DailyEntry e : entryRepository.findAll()) {
            if (e.getCustomerId() == null) continue;
            owed.merge(e.getCustomerId(), e.getTotal(), Double::sum);
            counts.merge(e.getCustomerId(), 1, Integer::sum);
        }
        for (Payment pay : paymentRepository.findAll()) {
            if (!pay.isConfirmed() || pay.getCustomerId() == null) continue;
            owed.merge(pay.getCustomerId(), -pay.getAmount(), Double::sum);
        }

        List<BreakdownResponse.Row> rows = owed.entrySet().stream()
                .filter(en -> BillingService.round2(en.getValue()) > 0)
                .map(en -> new BreakdownResponse.Row(
                        en.getKey(),
                        names.getOrDefault(en.getKey(), "Deleted customer"),
                        BillingService.round2(en.getValue()),
                        plural(counts.getOrDefault(en.getKey(), 0), "entry", "entries")))
                .sorted(Comparator.comparingDouble(BreakdownResponse.Row::amount).reversed())
                .toList();
        return new BreakdownResponse("OUTSTANDING", "Total outstanding",
                plural(rows.size(), "customer owes money", "customers owe money"),
                sum(rows), rows);
    }

    /** Confirmed payments in the month, split the way the two cards split them. */
    private BreakdownResponse collected(boolean wantCash, Map<String, String> names,
                                        LocalDate from, LocalDate to, String monthName) {
        Map<String, Double> paid = new java.util.HashMap<>();
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (Payment pay : paymentRepository.findAll()) {
            LocalDate d = pay.getPaymentDate();
            if (!pay.isConfirmed() || d == null || pay.getCustomerId() == null) continue;
            if (d.isBefore(from) || d.isAfter(to)) continue;
            if (isCash(pay.getMode()) != wantCash) continue;
            paid.merge(pay.getCustomerId(), pay.getAmount(), Double::sum);
            counts.merge(pay.getCustomerId(), 1, Integer::sum);
        }

        List<BreakdownResponse.Row> rows = new ArrayList<>(paid.entrySet().stream()
                .map(en -> new BreakdownResponse.Row(
                        en.getKey(),
                        names.getOrDefault(en.getKey(), "Deleted customer"),
                        BillingService.round2(en.getValue()),
                        plural(counts.getOrDefault(en.getKey(), 0), "payment", "payments")))
                .sorted(Comparator.comparingDouble(BreakdownResponse.Row::amount).reversed())
                .toList());

        // Counter sales have no khata customer but are real money in the same
        // bucket, so they get their own unlinked row rather than being dropped —
        // without it the rows would not add up to the card.
        double counter = 0;
        int counterCount = 0;
        for (ExtraSale x : extraSaleRepository.findAll()) {
            LocalDate d = x.getSaleDate();
            if (d == null || d.isBefore(from) || d.isAfter(to)) continue;
            if (isCash(x.getPaymentMode()) != wantCash) continue;
            counter += x.getTotal();
            counterCount++;
        }
        if (counterCount > 0) {
            rows.add(new BreakdownResponse.Row(null, "Counter sales (walk-in)",
                    BillingService.round2(counter), plural(counterCount, "sale", "sales")));
        }

        return new BreakdownResponse(wantCash ? "CASH" : "ONLINE",
                monthName + (wantCash ? " cash collected" : " online collected"),
                wantCash ? "Hand-to-hand, counter sales included" : "UPI, bank transfer and other",
                sum(rows), rows);
    }

    /**
     * Who was sold to over a date range — the click-through on the two sales
     * cards. Counter sales ride along as one unlinked row because the cards
     * count them too.
     */
    private BreakdownResponse sales(Map<String, String> names, LocalDate from, LocalDate to,
                                    String type, String title, String subtitle) {
        Map<String, Double> sold = new java.util.HashMap<>();
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (DailyEntry e : entryRepository.findInRange(from, to)) {
            String key = e.getCustomerId() == null ? "" : e.getCustomerId();
            sold.merge(key, e.getTotal(), Double::sum);
            counts.merge(key, 1, Integer::sum);
        }

        List<BreakdownResponse.Row> rows = new ArrayList<>(sold.entrySet().stream()
                .map(en -> new BreakdownResponse.Row(
                        en.getKey().isEmpty() ? null : en.getKey(),
                        en.getKey().isEmpty() ? "Entries without a customer"
                                : names.getOrDefault(en.getKey(), "Deleted customer"),
                        BillingService.round2(en.getValue()),
                        plural(counts.getOrDefault(en.getKey(), 0), "entry", "entries")))
                .sorted(Comparator.comparingDouble(BreakdownResponse.Row::amount).reversed())
                .toList());

        double counter = 0;
        int counterCount = 0;
        for (ExtraSale x : extraSaleRepository.findInRange(from, to)) {
            counter += x.getTotal();
            counterCount++;
        }
        if (counterCount > 0) {
            rows.add(new BreakdownResponse.Row(null, "Counter sales (walk-in)",
                    BillingService.round2(counter), plural(counterCount, "sale", "sales")));
        }

        return new BreakdownResponse(type, title, subtitle, sum(rows), rows);
    }

    /** The Extra Sells counter, product by product. */
    private BreakdownResponse walkin(LocalDate from, LocalDate to, String monthName) {
        Map<String, Double> amount = new LinkedHashMap<>();
        Map<String, Integer> counts = new java.util.HashMap<>();
        Map<String, Double> qty = new java.util.HashMap<>();
        Map<String, String> units = new java.util.HashMap<>();
        for (ExtraSale x : extraSaleRepository.findInRange(from, to)) {
            String key = (x.getProductName() == null || x.getProductName().isBlank())
                    ? "Other" : x.getProductName();
            amount.merge(key, x.getTotal(), Double::sum);
            counts.merge(key, 1, Integer::sum);
            qty.merge(key, x.getQuantity(), Double::sum);
            units.putIfAbsent(key, x.getUnit());
        }

        List<BreakdownResponse.Row> rows = amount.entrySet().stream()
                .map(en -> new BreakdownResponse.Row(null, en.getKey(),
                        BillingService.round2(en.getValue()),
                        plural(counts.getOrDefault(en.getKey(), 0), "sale", "sales")
                                + " · " + trim(qty.getOrDefault(en.getKey(), 0.0))
                                + (units.get(en.getKey()) == null ? "" : " " + units.get(en.getKey()))))
                .sorted(Comparator.comparingDouble(BreakdownResponse.Row::amount).reversed())
                .toList();

        return new BreakdownResponse("WALKIN", monthName + " walk-in sales",
                "Counter sales, product by product", sum(rows), rows);
    }

    /** Spending for the month, grouped the way it is entered — by category. */
    private BreakdownResponse expenses(LocalDate from, LocalDate to, String monthName) {
        Map<String, Double> spent = new LinkedHashMap<>();
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (Expense x : expenseRepository.findInRange(from, to)) {
            String key = (x.getCategory() == null || x.getCategory().isBlank()) ? "Other" : x.getCategory();
            spent.merge(key, x.getAmount(), Double::sum);
            counts.merge(key, 1, Integer::sum);
        }

        List<BreakdownResponse.Row> rows = spent.entrySet().stream()
                .map(en -> new BreakdownResponse.Row(null, en.getKey(),
                        BillingService.round2(en.getValue()),
                        plural(counts.getOrDefault(en.getKey(), 0), "expense", "expenses")))
                .sorted(Comparator.comparingDouble(BreakdownResponse.Row::amount).reversed())
                .toList();

        return new BreakdownResponse("EXPENSES", monthName + " expenses",
                "Category by category", sum(rows), rows);
    }

    /**
     * The three numbers the profit card is made of. Expenses are a negative
     * row rather than a footnote, so the rows still add up to the card.
     */
    private BreakdownResponse profit(LocalDate from, LocalDate to, String monthName) {
        double khata = 0;
        int khataCount = 0;
        for (DailyEntry e : entryRepository.findInRange(from, to)) {
            khata += e.getTotal();
            khataCount++;
        }
        double counter = 0;
        int counterCount = 0;
        for (ExtraSale x : extraSaleRepository.findInRange(from, to)) {
            counter += x.getTotal();
            counterCount++;
        }
        double spent = 0;
        int spentCount = 0;
        for (Expense x : expenseRepository.findInRange(from, to)) {
            spent += x.getAmount();
            spentCount++;
        }

        List<BreakdownResponse.Row> rows = new ArrayList<>();
        rows.add(new BreakdownResponse.Row(null, "Khata sales", BillingService.round2(khata),
                plural(khataCount, "entry", "entries")));
        rows.add(new BreakdownResponse.Row(null, "Counter sales (walk-in)", BillingService.round2(counter),
                plural(counterCount, "sale", "sales")));
        rows.add(new BreakdownResponse.Row(null, "Expenses", BillingService.round2(-spent),
                plural(spentCount, "expense", "expenses")));

        return new BreakdownResponse("PROFIT", monthName + " profit",
                "Sales minus expenses", sum(rows), rows);
    }

    /** Everyone on the khata, with what they owe right now. */
    private BreakdownResponse customers(Map<String, String> names) {
        Map<String, Double> owed = new java.util.HashMap<>();
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (DailyEntry e : entryRepository.findAll()) {
            if (e.getCustomerId() == null) continue;
            owed.merge(e.getCustomerId(), e.getTotal(), Double::sum);
            counts.merge(e.getCustomerId(), 1, Integer::sum);
        }
        for (Payment pay : paymentRepository.findAll()) {
            if (!pay.isConfirmed() || pay.getCustomerId() == null) continue;
            owed.merge(pay.getCustomerId(), -pay.getAmount(), Double::sum);
        }

        // Driven by the customer list, not by the entries, so a customer who has
        // never been billed still appears — the card counts them.
        List<BreakdownResponse.Row> rows = names.entrySet().stream()
                .map(en -> new BreakdownResponse.Row(en.getKey(), en.getValue(),
                        BillingService.round2(owed.getOrDefault(en.getKey(), 0.0)),
                        plural(counts.getOrDefault(en.getKey(), 0), "entry", "entries")))
                .sorted(Comparator.comparingDouble(BreakdownResponse.Row::amount).reversed())
                .toList();

        return new BreakdownResponse("CUSTOMERS", "Customers",
                plural(rows.size(), "customer on the khata", "customers on the khata"),
                sum(rows), rows);
    }

    private static double sum(List<BreakdownResponse.Row> rows) {
        return BillingService.round2(rows.stream().mapToDouble(BreakdownResponse.Row::amount).sum());
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** "2" reads better than "2.0" in a row's small print. */
    private static String trim(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(BillingService.round2(v));
    }

    /** Full breakdown for a single day — shown when a chart bar is clicked. */
    public DayDetail day(LocalDate date) {
        List<DailyEntry> entries = entryRepository.findInRange(date, date);
        List<Expense> expenses = expenseRepository.findInRange(date, date);
        List<ExtraSale> extras = extraSaleRepository.findInRange(date, date);

        double extraTotal = extras.stream().mapToDouble(ExtraSale::getTotal).sum();
        double sales = entries.stream().mapToDouble(DailyEntry::getTotal).sum() + extraTotal;
        double spent = expenses.stream().mapToDouble(Expense::getAmount).sum();

        List<DayDetail.EntryRow> entryRows = entries.stream()
                .sorted(Comparator.comparingDouble(DailyEntry::getTotal).reversed())
                .map(e -> new DayDetail.EntryRow(
                        e.getCustomerName(),
                        e.getProductName(),
                        BillingService.round2(e.getQuantity()),
                        e.getUnit(),
                        BillingService.round2(e.getRate()),
                        BillingService.round2(e.getTotal()),
                        e.isPaid()))
                .toList();

        List<DayDetail.ExpenseRow> expenseRows = expenses.stream()
                .sorted(Comparator.comparingDouble(Expense::getAmount).reversed())
                .map(x -> new DayDetail.ExpenseRow(
                        x.getCategory(),
                        x.getNote(),
                        BillingService.round2(x.getQuantity() > 0 ? x.getQuantity() : 1),
                        x.getUnit(),
                        BillingService.round2(x.getUnitAmount() > 0 ? x.getUnitAmount() : x.getAmount()),
                        BillingService.round2(x.getAmount())))
                .toList();

        List<DayDetail.ExtraRow> extraRows = extras.stream()
                .map(x -> new DayDetail.ExtraRow(
                        x.getCustomerName(),
                        x.getProductName(),
                        BillingService.round2(x.getQuantity()),
                        x.getUnit(),
                        BillingService.round2(x.getRate()),
                        BillingService.round2(x.getTotal()),
                        x.getPaymentMode()))
                .toList();

        return new DayDetail(
                date.toString(),
                FULL_DAY_LABEL.format(date),
                BillingService.round2(sales),
                BillingService.round2(spent),
                BillingService.round2(sales - spent),
                entryRows.size(),
                expenseRows.size(),
                BillingService.round2(extraTotal),
                extraRows.size(),
                entryRows,
                expenseRows,
                extraRows);
    }
}
