package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class PriceCalculationService {

    private final int scale;
    private final BigDecimal spreadRate;

    public PriceCalculationService(AppProperties appProperties) {
        this.scale = appProperties.getScale();
        this.spreadRate = appProperties.getForex().getSpreadRate();
    }

    public void applyDirectSnapshot(Forex forex, YahooQuoteDto quote) {
        forex.applyYahooSnapshot(quote.regularMarketPrice(), quote.previousClose(), spreadRate, scale);
    }

    public void applySyntheticSnapshot(Forex forex, YahooQuoteDto pairQuote,
                                       BigDecimal usdtryPrice, BigDecimal usdtryChange,
                                       boolean isUsdBase) {
        BigDecimal pairPrice = pairQuote.regularMarketPrice();
        if (pairPrice == null || pairPrice.signum() == 0) return;

        BigDecimal syntheticPrice = isUsdBase
                ? safeDivide(usdtryPrice, pairPrice)
                : safeMultiply(usdtryPrice, pairPrice);
        if (syntheticPrice == null) return;

        BigDecimal syntheticPreviousClose = null;
        BigDecimal pairPreviousClose = pairQuote.previousClose();
        if (pairPreviousClose != null && pairPreviousClose.signum() > 0) {
            BigDecimal usdtryPreviousClose = usdtryChange != null
                    ? usdtryPrice.subtract(usdtryChange)
                    : usdtryPrice;
            if (usdtryPreviousClose.signum() > 0) {
                syntheticPreviousClose = isUsdBase
                        ? safeDivide(usdtryPreviousClose, pairPreviousClose)
                        : safeMultiply(usdtryPreviousClose, pairPreviousClose);
            }
        }

        forex.applySyntheticPrice(syntheticPrice, syntheticPreviousClose, spreadRate, scale);
    }

    public List<YahooCandleDto> buildSyntheticCandles(List<YahooCandleDto> pairCandles,
                                                      Map<String, ForexCandle> usdtryCandleByDate,
                                                      boolean isUsdBase) {
        List<YahooCandleDto> synthetic = new ArrayList<>(pairCandles.size());
        for (YahooCandleDto pair : pairCandles) {
            String dateKey = pair.candleDate().toLocalDate().toString();
            ForexCandle usdtryCandle = usdtryCandleByDate.get(dateKey);
            if (usdtryCandle == null) {
                log.debug("Skipping synthetic candle for date {}: no USDTRY candle", dateKey);
                continue;
            }
            YahooCandleDto syntheticCandle = isUsdBase
                    ? buildUsdBaseCandle(pair, usdtryCandle)
                    : buildNonUsdBaseCandle(pair, usdtryCandle);
            if (syntheticCandle != null) {
                synthetic.add(syntheticCandle);
            }
        }
        return synthetic;
    }

    private YahooCandleDto buildUsdBaseCandle(YahooCandleDto pair, ForexCandle usdtry) {
        BigDecimal open = safeDivide(usdtry.getOpen(), pair.open());
        BigDecimal high = safeDivide(usdtry.getHigh(), pair.low());
        BigDecimal low = safeDivide(usdtry.getLow(), pair.high());
        BigDecimal close = safeDivide(usdtry.getClose(), pair.close());
        if (open == null || high == null || low == null || close == null) return null;
        return new YahooCandleDto(pair.candleDate(), open, maxOf(open, high, close),
                minOf(open, low, close), close, pair.volume());
    }

    private YahooCandleDto buildNonUsdBaseCandle(YahooCandleDto pair, ForexCandle usdtry) {
        BigDecimal open = safeMultiply(usdtry.getOpen(), pair.open());
        BigDecimal high = safeMultiply(usdtry.getHigh(), pair.high());
        BigDecimal low = safeMultiply(usdtry.getLow(), pair.low());
        BigDecimal close = safeMultiply(usdtry.getClose(), pair.close());
        if (open == null || high == null || low == null || close == null) return null;
        return new YahooCandleDto(pair.candleDate(), open, maxOf(open, high, close),
                minOf(open, low, close), close, pair.volume());
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) return null;
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }

    private BigDecimal safeMultiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.multiply(b).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal maxOf(BigDecimal a, BigDecimal b, BigDecimal c) {
        BigDecimal max = a;
        if (b.compareTo(max) > 0) max = b;
        if (c.compareTo(max) > 0) max = c;
        return max;
    }

    private BigDecimal minOf(BigDecimal a, BigDecimal b, BigDecimal c) {
        BigDecimal min = a;
        if (b.compareTo(min) < 0) min = b;
        if (c.compareTo(min) < 0) min = c;
        return min;
    }
}
