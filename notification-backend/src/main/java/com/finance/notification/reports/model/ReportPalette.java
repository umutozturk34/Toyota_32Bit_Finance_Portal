package com.finance.notification.reports.model;

/**
 * Theme color palette for the PDF report (background, text, borders, success/danger accents).
 * Provides DARK and LIGHT presets selected by theme name, defaulting to DARK.
 */
public record ReportPalette(
        String bg,
        String card,
        String border,
        String fg,
        String muted,
        String subtle,
        String infoBg,
        String successFg,
        String successBg,
        String dangerFg,
        String dangerBg,
        // Dedicated "closed/inactive" badge tones. The earlier reuse of {@code subtle} on
        // {@code infoBg} produced near-invisible KAPALI labels in the dark theme — both colors
        // were almost the same as the page background. These pair high enough contrast on
        // both themes for the badge to read at a glance without competing with AÇIK.
        String closedFg,
        String closedBg
) {
    public static final ReportPalette DARK = new ReportPalette(
            "#070912", "#0d111c", "#1a2030",
            "#f0f2f7", "#8b93a8", "#5c6577",
            "#0a0d16",
            "#34d399", "#0e2d24",
            "#f87171", "#3a0f12",
            "#cbd5e1", "#1e293b"
    );

    public static final ReportPalette LIGHT = new ReportPalette(
            "#f4f5f9", "#ffffff", "#e7e9f1",
            "#0d111e", "#5c6479", "#7c83a0",
            "#f7f8fc",
            "#059669", "#d1fae5",
            "#dc2626", "#fee2e2",
            "#475569", "#e2e8f0"
    );

    public static ReportPalette of(String theme) {
        return "LIGHT".equalsIgnoreCase(theme) || "light".equalsIgnoreCase(theme) ? LIGHT : DARK;
    }
}
