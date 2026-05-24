package com.finance.notification.reports.dto;

public enum ThemeVariant {
    LIGHT, DARK;

    public String templatePath() {
        return this == DARK ? "pdf/portfolio-report-dark" : "pdf/portfolio-report-light";
    }
}
