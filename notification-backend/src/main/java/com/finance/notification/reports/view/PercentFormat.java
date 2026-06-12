package com.finance.notification.reports.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Locale-aware percentage formatter for PDF templates. Mirrors the frontend allocation rule: an allocation
 * share that genuinely exists (value &gt; 0) but rounds to zero at the display precision renders the sentinel
 * {@code <0,1%} instead of {@code 0,0%}, so a real slice never reads as exactly 0%. The underlying numbers
 * (from the largest-remainder rounding that must sum to 100) are untouched — this only changes the label.
 */
public final class PercentFormat {

    private static final int SHARE_DECIMALS = 1;

    private final char decimalSep;

    /**
     * @param locale selects the decimal separator: Turkish ({@code tr}) uses {@code ,}, everything else {@code .}.
     */
    public PercentFormat(Locale locale) {
        this.decimalSep = (locale != null && "tr".equalsIgnoreCase(locale.getLanguage())) ? ',' : '.';
    }

    /**
     * Allocation share label: the rounded percent (e.g. {@code 12,3%}), or {@code <0,1%} when the slice has a
     * strictly-positive value yet rounds to {@code 0,0%} at one decimal. A null share renders as an em dash.
     */
    public String share(BigDecimal sharePct, BigDecimal value) {
        if (sharePct == null) return "—";
        BigDecimal rounded = sharePct.setScale(SHARE_DECIMALS, RoundingMode.HALF_UP);
        if (value != null && value.signum() > 0 && rounded.signum() == 0) {
            return "<0" + decimalSep + "1%";
        }
        return rounded.toPlainString().replace('.', decimalSep) + "%";
    }
}
