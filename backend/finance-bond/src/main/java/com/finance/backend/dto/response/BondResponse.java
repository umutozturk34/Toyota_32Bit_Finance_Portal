package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BondResponse(
        String seriesCode,
        String isinCode,
        BigDecimal couponRate,
        BigDecimal simpleYield,
        BigDecimal baseIndex,
        LocalDate maturityStart,
        LocalDate maturityEnd,
        LocalDate nextCouponDate,
        String bondType,
        String issuer,
        LocalDateTime lastUpdated
) {}
