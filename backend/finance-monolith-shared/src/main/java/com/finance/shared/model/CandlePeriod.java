package com.finance.shared.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.UnaryOperator;

import lombok.Getter;

@Getter
public enum CandlePeriod {

    ONE_WEEK("1W", 0, end -> end.minusWeeks(1)),
    ONE_MONTH("1M", 1, end -> end.minusMonths(1)),
    THREE_MONTHS("3M", 3, end -> end.minusMonths(3)),
    SIX_MONTHS("6M", 6, end -> end.minusMonths(6)),
    ONE_YEAR("1Y", 12, end -> end.minusYears(1)),
    THREE_YEARS("3Y", 36, end -> end.minusYears(3)),
    FIVE_YEARS("5Y", 60, end -> end.minusYears(5)),
    ALL("ALL", 0, end -> LocalDateTime.of(1970, 1, 1, 0, 0));

    private static final LocalDate EPOCH_DATE = LocalDate.of(1970, 1, 1);

    private final String code;
    private final int months;
    private final UnaryOperator<LocalDateTime> startResolver;

    CandlePeriod(String code, int months, UnaryOperator<LocalDateTime> startResolver) {
        this.code = code;
        this.months = months;
        this.startResolver = startResolver;
    }

    public LocalDateTime toStartDateTime(LocalDateTime end) {
        return startResolver.apply(end);
    }

    public LocalDateTime toStartDateTime() {
        return toStartDateTime(LocalDateTime.now()).toLocalDate().atStartOfDay();
    }

    public LocalDate toStartDate() {
        if (this == ALL) return EPOCH_DATE;
        if (this == ONE_WEEK) return LocalDate.now().minusWeeks(1);
        return LocalDate.now().minusMonths(months);
    }

    public static CandlePeriod fromCode(String code) {
        if (code == null) return ONE_MONTH;
        for (CandlePeriod value : values()) {
            if (value.code.equalsIgnoreCase(code)) return value;
        }
        return ONE_MONTH;
    }
}
