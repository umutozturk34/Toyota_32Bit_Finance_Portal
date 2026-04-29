package com.finance.backend.dto.internal;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;

import java.util.List;

public record YahooChartFullResult(YahooQuoteDto quote, List<YahooCandleDto> candles) {
}
