package com.aryavart.dairy.dto;

import java.util.List;

/** Everything that happened on one day — powers the chart click-through popup. */
public record DayDetail(
        String date,
        String label,
        double sales,
        double expenses,
        double profit,
        int entryCount,
        int expenseCount,
        /** Walk-in counter sales on this day — included in the sales figure. */
        double extraTotal,
        int extraCount,
        List<EntryRow> entries,
        List<ExpenseRow> expenseRows,
        List<ExtraRow> extraRows) {

    public record EntryRow(String customerName, String productName, double quantity,
                           String unit, double rate, double total, boolean paid) {
    }

    public record ExpenseRow(String category, String note, double quantity,
                             String unit, double unitAmount, double amount) {
    }

    public record ExtraRow(String customerName, String productName, double quantity,
                           String unit, double rate, double total, String paymentMode) {
    }
}
