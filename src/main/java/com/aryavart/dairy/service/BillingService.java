package com.aryavart.dairy.service;

import com.aryavart.dairy.dto.BillResponse;
import com.aryavart.dairy.model.DailyEntry;
import com.aryavart.dairy.model.Payment;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.DailyEntryRepository;
import com.aryavart.dairy.repository.PaymentRepository;
import com.aryavart.dairy.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class BillingService {

    private final UserRepository userRepository;
    private final DailyEntryRepository entryRepository;
    private final PaymentRepository paymentRepository;

    public BillingService(UserRepository userRepository,
                          DailyEntryRepository entryRepository,
                          PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.entryRepository = entryRepository;
        this.paymentRepository = paymentRepository;
    }

    public BillResponse getBill(String customerId, LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'To' date cannot be before 'From' date");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        List<DailyEntry> periodEntries = entryRepository
                .findForCustomerInRange(
                        customerId, from, to);
        double periodTotal = periodEntries.stream().mapToDouble(DailyEntry::getTotal).sum();

        double lifetimePurchases = entryRepository.findByCustomerIdOrderByEntryDateAsc(customerId)
                .stream().mapToDouble(DailyEntry::getTotal).sum();

        List<Payment> allPayments = paymentRepository.findByCustomerIdOrderByPaymentDateDesc(customerId);
        double lifetimePaid = allPayments.stream().mapToDouble(Payment::getAmount).sum();

        final LocalDate f = from;
        final LocalDate t = to;
        List<Payment> periodPayments = allPayments.stream()
                .filter(p -> p.getPaymentDate() != null
                        && !p.getPaymentDate().isBefore(f)
                        && !p.getPaymentDate().isAfter(t))
                .toList();
        double periodPaid = periodPayments.stream().mapToDouble(Payment::getAmount).sum();

        double outstanding = round2(lifetimePurchases - lifetimePaid);

        return new BillResponse(customer.getId(), customer.getName(), customer.getPhone(), customer.getAddress(),
                from, to, periodEntries, round2(periodTotal), periodPayments, round2(periodPaid),
                round2(lifetimePurchases), round2(lifetimePaid), outstanding);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
