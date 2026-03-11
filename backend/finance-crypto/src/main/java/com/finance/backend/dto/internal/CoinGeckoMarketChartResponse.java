package com.finance.backend.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record CoinGeckoMarketChartResponse(
        List<List<BigDecimal>> prices,
        @JsonProperty("total_volumes") List<List<BigDecimal>> totalVolumes
) {}
