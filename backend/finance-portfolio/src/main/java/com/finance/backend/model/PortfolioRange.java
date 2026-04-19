package com.finance.backend.model;

import java.time.LocalDateTime;

public enum PortfolioRange {
    ONE_MONTH("1M") {
        @Override
        public LocalDateTime toStartDateTime(LocalDateTime end) {
            return end.minusMonths(1);
        }
    },
    THREE_MONTHS("3M") {
        @Override
        public LocalDateTime toStartDateTime(LocalDateTime end) {
            return end.minusMonths(3);
        }
    },
    SIX_MONTHS("6M") {
        @Override
        public LocalDateTime toStartDateTime(LocalDateTime end) {
            return end.minusMonths(6);
        }
    },
    ONE_YEAR("1Y") {
        @Override
        public LocalDateTime toStartDateTime(LocalDateTime end) {
            return end.minusYears(1);
        }
    },
    ALL("ALL") {
        @Override
        public LocalDateTime toStartDateTime(LocalDateTime end) {
            return end.minusYears(10);
        }
    };

    private final String code;

    PortfolioRange(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public abstract LocalDateTime toStartDateTime(LocalDateTime end);

    public static PortfolioRange fromCode(String code) {
        if (code == null) return ONE_MONTH;
        for (PortfolioRange value : values()) {
            if (value.code.equalsIgnoreCase(code)) return value;
        }
        return ONE_MONTH;
    }
}
