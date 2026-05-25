package com.finance.notification.reports.view;

import java.math.BigDecimal;

public record RealizedViewItem(
        String label,
        BigDecimal realized,
        BigDecimal cost,
        double barPct
) {}
