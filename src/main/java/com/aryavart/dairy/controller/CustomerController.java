package com.aryavart.dairy.controller;

import com.aryavart.dairy.dto.BillResponse;
import com.aryavart.dairy.model.User;
import com.aryavart.dairy.repository.UserRepository;
import com.aryavart.dairy.service.BillingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/** Endpoints for the logged-in customer. */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final BillingService billingService;
    private final UserRepository userRepository;

    public CustomerController(BillingService billingService, UserRepository userRepository) {
        this.billingService = billingService;
        this.userRepository = userRepository;
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
}
