package com.aryavart.dairy.dto;

import java.util.List;

public record StatsResponse(
        /** Selected month, e.g. "2026-08". */
        String month,
        /** Human label for the selected month, e.g. "August 2026". */
        String monthLabel,
        double todaySales,
        double monthSales,
        double totalSales,
        /** Walk-in counter sales — already included in the sales figures above. */
        double todayExtraSales,
        double monthExtraSales,
        double totalExtraSales,
        double totalPaymentsReceived,
        double totalOutstanding,
        double todayExpenses,
        double monthExpenses,
        double totalExpenses,
        double monthProfit,
        long customerCount,
        long productCount,
        long todayEntryCount,
        List<ProductSale> productSales,
        /** One point per day of the selected month — sales and expenses together. */
        List<DayPoint> days,
        /** Last 12 months, for the trend chart. */
        List<DatePoint> monthly,
        /** Dropdown options: the last 12 months. */
        List<MonthOption> months) {

    public record DatePoint(String label, double total) {
    }

    public record DayPoint(String date, String label, double sales, double expenses,
                           /** Walk-in slice of that day's sales. */
                           double extra) {
    }

    public record MonthOption(String value, String label) {
    }

    /** Per-product totals for today and the selected month. */
    public record ProductSale(
            String productId,
            String productName,
            String unit,
            double todayQty,
            double todayAmount,
            double monthQty,
            double monthAmount) {
    }
}
