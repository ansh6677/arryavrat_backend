package com.aryavart.dairy.controller;

import com.aryavart.dairy.dto.BillResponse;
import com.aryavart.dairy.dto.PaymentClaimRequest;
import com.aryavart.dairy.model.Payment;
import com.aryavart.dairy.model.ProcessedRequest;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.PaymentRepository;
import com.aryavart.dairy.repository.ProcessedRequestRepository;
import com.aryavart.dairy.repository.UserRepository;
import com.aryavart.dairy.service.BillingService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/** Endpoints for the logged-in customer. */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final BillingService billingService;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final ProcessedRequestRepository processedRequestRepository;

    public CustomerController(BillingService billingService,
                              UserRepository userRepository,
                              PaymentRepository paymentRepository,
                              ProcessedRequestRepository processedRequestRepository) {
        this.billingService = billingService;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.processedRequestRepository = processedRequestRepository;
    }

    @GetMapping("/me")
    public User me(Authentication authentication) {
        return userRepository.findById(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /** From-To date-range bill: entries, payments and outstanding. */
    @GetMapping("/bill")
    public BillResponse bill(Authentication authentication,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return billingService.getBill(authentication.getName(), from, to);
    }

    /**
     * "I paid you by UPI" — saved as a PENDING payment so it shows up on the
     * customer's dashboard and in the admin's Confirm list straight away, but
     * does NOT touch the outstanding until staff verify the transfer.
     *
     * Amount defaults to the current outstanding, and a re-tap while one claim
     * is already open just returns that claim instead of piling up duplicates.
     */
    @PostMapping("/payments/claim")
    public Payment claimPayment(Authentication authentication, @RequestBody(required = false) PaymentClaimRequest req) {
        String customerId = authentication.getName();
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        // Already reported and not verified yet? Show that one — one open claim
        // per customer keeps the admin's list honest.
        List<Payment> open = paymentRepository
                .findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, Payment.PENDING);
        if (!open.isEmpty()) return open.get(0);

        double outstanding = billingService.getBill(customerId, null, null).outstanding();
        double amount = (req != null && req.amount() != null && req.amount() > 0)
                ? req.amount()
                : outstanding;
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is nothing due on your khata right now.");
        }

        // Same double-tap guard the admin endpoints use.
        if (req != null && req.requestId() != null && !req.requestId().isBlank()) {
            try {
                processedRequestRepository.insert(new ProcessedRequest(req.requestId()));
            } catch (DuplicateKeyException e) {
                List<Payment> again = paymentRepository
                        .findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, Payment.PENDING);
                if (!again.isEmpty()) return again.get(0);
            }
        }

        String ref = (req == null || req.ref() == null) ? "" : req.ref().trim();

        Payment claim = new Payment();
        claim.setCustomerId(customer.getId());
        claim.setCustomerName(customer.getName());
        claim.setAmount(BillingService.round2(amount));
        claim.setPaymentDate(LocalDate.now());
        claim.setMode("UPI");
        claim.setStatus(Payment.PENDING);
        claim.setClaimedRef(ref.isEmpty() ? null : ref);
        claim.setNote("Reported by customer via UPI"
                + (ref.isEmpty() ? "" : " — ref " + ref)
                + (req != null && req.note() != null && !req.note().isBlank() ? " — " + req.note().trim() : ""));
        return paymentRepository.save(claim);
    }

    /** The customer's own open claims, so the dashboard can show "waiting for confirmation". */
    @GetMapping("/payments/claim")
    public List<Payment> myClaims(Authentication authentication) {
        return paymentRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(
                authentication.getName(), Payment.PENDING);
    }
}
