package com.finance.market.core.dto.external;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * One OHLCV bar parsed from Yahoo Finance chart responses: the {@code candleDate} timestamp with the
 * open/high/low/close prices and traded {@code volume}. Maps an external Yahoo payload into a flat,
 * provider-neutral shape for downstream candle persistence.
 */
public record YahooCandleDto(
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}
