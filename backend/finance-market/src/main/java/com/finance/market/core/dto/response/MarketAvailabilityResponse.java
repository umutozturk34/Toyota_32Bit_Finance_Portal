package com.finance.market.core.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** The dates an asset has a known price, keyed by date, used to bound back-fill/valuation ranges. */
public record MarketAvailabilityResponse(Map<LocalDate, BigDecimal> prices) {}
