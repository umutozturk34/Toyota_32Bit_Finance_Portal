package com.finance.market.bond.dto.external;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondRateItemDto(
        LocalDate rateDate,
        BigDecimal couponRate,
        BigDecimal price
) {}
