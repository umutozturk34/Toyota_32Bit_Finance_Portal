package com.finance.notification.reports.view;

import java.math.BigDecimal;

/** Winners/losers entry for the template: realized P/L, cost and a bar width relative to the largest. */
public record RealizedViewItem(
        String label,
        BigDecimal realized,
        BigDecimal cost,
        double barPct
) {}
