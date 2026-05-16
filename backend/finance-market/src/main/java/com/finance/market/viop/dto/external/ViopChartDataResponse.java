package com.finance.market.viop.dto.external;

import java.math.BigDecimal;
import java.util.List;

public record ViopChartDataResponse(
        List<List<BigDecimal>> data,
        String timestamp
) { }
