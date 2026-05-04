package com.finance.common.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record MarketAvailabilityResponse(Map<LocalDate, BigDecimal> prices) {}
