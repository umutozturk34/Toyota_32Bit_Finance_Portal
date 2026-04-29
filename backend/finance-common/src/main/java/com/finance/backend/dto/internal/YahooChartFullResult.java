package com.finance.backend.dto.internal;

import com.finance.backend.dto.external.YahooCandleDto;

import java.util.List;

public record YahooChartFullResult<Q>(Q quote, List<YahooCandleDto> candles) {
}
