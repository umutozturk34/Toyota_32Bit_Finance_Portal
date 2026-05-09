package com.finance.market.bond.dto.external;

import java.time.LocalDate;

public record BondSerieDto(
        String isin,
        String serieCode,
        String serieName,
        LocalDate maturityStart,
        LocalDate maturityEnd
) {}
