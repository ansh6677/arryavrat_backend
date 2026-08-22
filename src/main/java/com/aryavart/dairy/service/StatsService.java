package com.aryavart.dairy.service;

import com.aryavart.dairy.dto.DayDetail;
import com.aryavart.dairy.dto.StatsResponse;
import com.aryavart.dairy.dto.StatsResponse.DatePoint;
import com.aryavart.dairy.dto.StatsResponse.DayPoint;
import com.aryavart.dairy.dto.StatsResponse.MonthOption;
import com.aryavart.dairy.dto.StatsResponse.ProductSale;
import com.aryavart.dairy.model.DailyEntry;
import com.aryavart.dairy.model.Expense;
import com.aryavart.dairy.model.Payment;
import com.aryavart.dairy.repository.DailyEntryRepository;
import com.aryavart.dairy.repository.ExpenseRepository;
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
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public StatsService(DailyEntryRepository entryRepository, PaymentRepository paymentRepository,
                        ExpenseRepository expenseRepository, UserRepository userRepository,
                        ProductRepository productRepository) {
        this.entryRepository = entryRepository;
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
        List<Payment> allPayments = paymentRepository.findAll();
        List<Expense> allExpenses = expenseRepository.findAll();

        double totalSales = allEntries.stream().mapToDouble(DailyEntry::getTotal).sum();
        double totalPaid = allPayments.stream().mapToDouble(Payment::getAmount).sum();
        double totalExpenses = allExpenses.stream().mapToDouble(Expense::getAmount).sum();

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

        List<DayPoint> days = new ArrayList<>();
        for (LocalDate d = monthStart; !d.isAfter(monthEnd); d = d.plusDays(1)) {
            days.add(new DayPoint(
                    d.toString(),
                    DAY_LABEL.format(d),
                    BillingService.round2(salesByDate.getOrDefault(d, 0.0)),
                    BillingService.round2(expenseByDate.getOrDefault(d, 0.0))));
        }

        Map<YearMonth, Double> byMonth = allEntries.stream()
                .filter(e -> e.getEntryDate() != null)
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getEntryDate()),
                        Collectors.summingDouble(DailyEntry::getTotal)));

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
                BillingService.round2(todaySales),
                BillingService.round2(monthSales),
                BillingService.round2(totalSales),
                BillingService.round2(totalPaid),
                BillingService.round2(totalSales - totalPaid),
                BillingService.round2(todayExpenses),
                BillingService.round2(monthExpenses),
                BillingService.round2(totalExpenses),
                BillingService.round2(monthSales - monthExpenses),
                userRepository.countByRole("CUSTOMER"),
                productRepository.count(),
                todayEntryCount,
                productSales,
                days,
                monthly,
                months);
    }

    /** Full breakdown for a single day — shown when a chart bar is clicked. */
    public DayDetail day(LocalDate date) {
        List<DailyEntry> entries = entryRepository.findInRange(date, date);
        List<Expense> expenses = expenseRepository.findInRange(date, date);

        double sales = entries.stream().mapToDouble(DailyEntry::getTotal).sum();
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

        return new DayDetail(
                date.toString(),
                FULL_DAY_LABEL.format(date),
                BillingService.round2(sales),
                BillingService.round2(spent),
                BillingService.round2(sales - spent),
                entryRows.size(),
                expenseRows.size(),
                entryRows,
                expenseRows);
    }
}
