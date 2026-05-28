package com.finance.market.core.dto.internal;

import com.finance.market.core.dto.external.YahooCandleDto;

import java.util.List;

/** A Yahoo chart fetch's quote and candle list together; {@code Q} is the market's quote DTO type. */
public record YahooChartFullResult<Q>(Q quote, List<YahooCandleDto> candles) {
}
