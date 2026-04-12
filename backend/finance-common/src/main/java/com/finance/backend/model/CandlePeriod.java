package com.finance.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CandlePeriod {

    ONE_MONTH(1),
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    ONE_YEAR(12),
    FIVE_YEARS(60),
    ALL(0);

    private final int months;

    public LocalDateTime toStartDateTime() {
        if (this == ALL) {
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        return LocalDateTime.now().minusMonths(months).toLocalDate().atStartOfDay();
    }

    public LocalDate toStartDate() {
        if (this == ALL) {
            return LocalDate.of(1970, 1, 1);
        }
        return LocalDate.now().minusMonths(months);
    }
}
