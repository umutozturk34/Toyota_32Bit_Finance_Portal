package com.finance.market.bond.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondRateResponse(
        LocalDate date,
        BigDecimal rate,
        BigDecimal price
) {}
