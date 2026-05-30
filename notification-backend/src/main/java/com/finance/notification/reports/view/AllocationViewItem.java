package com.finance.notification.reports.view;

import java.math.BigDecimal;

/** Allocation donut entry prepared for the template: label, value, share percentage and slice color. */
public record AllocationViewItem(
        String label,
        BigDecimal value,
        BigDecimal sharePct,
        String color
) {}
