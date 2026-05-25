package com.finance.notification.reports.view;

import java.math.BigDecimal;

public record AllocationViewItem(
        String label,
        BigDecimal value,
        BigDecimal sharePct,
        String color
) {}
