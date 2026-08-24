package com.aryavart.dairy.dto;

import com.aryavart.dairy.model.DailyEntry;
import com.aryavart.dairy.model.Payment;

import java.time.LocalDate;
import java.util.List;

public record BillResponse(
        String customerId,
        String customerName,
        String phone,
        String address,
        LocalDate from,
        LocalDate to,
        List<DailyEntry> entries,
        double periodTotal,
        List<Payment> payments,
        double periodPaid,
        double lifetimePurchases,
        double lifetimePaid,
        /** Dues carried in from before this period: purchases minus payments up to the day before 'from'. */
        double previousBalance,
        double outstanding) {
}
