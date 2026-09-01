package com.aryavart.dairy.config;

import com.aryavart.dairy.model.DailyEntry;
import com.aryavart.dairy.model.Payment;
import com.aryavart.dairy.repository.DailyEntryRepository;
import com.aryavart.dairy.repository.PaymentRepository;
import com.aryavart.dairy.service.BillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * One-time cleanup: the first version of the "old payment" button wrongly
 * recorded past-month dues as RECEIVED payments (lowering the outstanding).
 * The concept is the opposite — those amounts are still owed. This runner
 * converts every legacy payment that carries a forPeriod into an unpaid
 * "old due" entry of the same amount/month/remarks and deletes the payment,
 * so the customer's outstanding goes back up to the truth. Idempotent:
 * after one run no forPeriod payments remain.
 */
@Component
@Order(20) // after DataSeeder
public class OldPaymentToDueMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OldPaymentToDueMigration.class);

    private final PaymentRepository paymentRepository;
    private final DailyEntryRepository entryRepository;

    public OldPaymentToDueMigration(PaymentRepository paymentRepository, DailyEntryRepository entryRepository) {
        this.paymentRepository = paymentRepository;
        this.entryRepository = entryRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Payment> legacy = paymentRepository.findAll().stream()
                .filter(p -> p.getForPeriod() != null && !p.getForPeriod().isBlank())
                .toList();
        if (legacy.isEmpty()) return;

        int converted = 0;
        for (Payment p : legacy) {
            YearMonth cycle;
            try {
                cycle = YearMonth.parse(p.getForPeriod().trim());
            } catch (Exception e) {
                log.warn("Old-payment migration: skipping payment {} with unparseable forPeriod '{}'",
                        p.getId(), p.getForPeriod());
                continue;
            }
            DailyEntry due = new DailyEntry();
            due.setCustomerId(p.getCustomerId());
            due.setCustomerName(p.getCustomerName());
            due.setProductId(null);
            due.setProductName("Old due — "
                    + cycle.format(DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH)));
            due.setUnit("");
            due.setQuantity(1);
            due.setRate(BillingService.round2(p.getAmount()));
            due.setTotal(BillingService.round2(p.getAmount()));
            due.setEntryDate(cycle.atEndOfMonth());
            due.setNote(p.getNote());
            due.setPaid(false);
            due.setOldDue(true);
            due.setForPeriod(cycle.toString());
            if (p.getCreatedAt() != null) due.setCreatedAt(p.getCreatedAt());
            entryRepository.save(due);
            paymentRepository.deleteById(p.getId());
            converted++;
        }
        log.info("Old-payment migration: converted {} legacy old-payment(s) into unpaid old-due entries.", converted);
    }
}
