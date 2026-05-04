package com.finance.common.dto.internal;

import com.finance.common.dto.external.YahooCandleDto;

import java.util.List;

public record YahooChartFullResult<Q>(Q quote, List<YahooCandleDto> candles) {
}
