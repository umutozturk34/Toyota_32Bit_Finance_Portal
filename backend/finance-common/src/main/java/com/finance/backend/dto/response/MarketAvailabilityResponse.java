package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record MarketAvailabilityResponse(
        String yearMonth,
        LocalDate from,
        LocalDate to,
        Map<String, BigDecimal> prices
) {}
