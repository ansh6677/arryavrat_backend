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
        List<EntryRow> entries,
        List<ExpenseRow> expenseRows) {

    public record EntryRow(String customerName, String productName, double quantity,
                           String unit, double rate, double total, boolean paid) {
    }

    public record ExpenseRow(String category, String note, double quantity,
                             String unit, double unitAmount, double amount) {
    }
}
