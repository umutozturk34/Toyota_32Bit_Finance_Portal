package com.finance.backend.dto.external;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondSnapshotDto(
        String seriesCode,
        String isinCode,
        BigDecimal cleanPrice,
        BigDecimal couponRate,
        LocalDate maturityStart,
        LocalDate maturityEnd,
        String serieName
) {}
