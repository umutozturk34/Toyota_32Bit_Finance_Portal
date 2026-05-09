package com.finance.market.core.dto.internal;

import com.finance.market.core.dto.external.YahooCandleDto;

import java.util.List;

public record YahooChartFullResult<Q>(Q quote, List<YahooCandleDto> candles) {
}
