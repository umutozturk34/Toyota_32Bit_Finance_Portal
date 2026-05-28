package com.finance.market.viop.dto.external;

import java.math.BigDecimal;
import java.util.List;

/** Raw chart-data response: {@code data} is a list of {@code [epochMillis, close]} pairs. */
public record ViopChartDataResponse(
        List<List<BigDecimal>> data,
        String timestamp
) { }
