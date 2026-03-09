package com.finance.backend.mapper;
import com.finance.backend.dto.external.TcmbRateDto;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Slf4j
@Component
public class ForexMapper {
    private static final int SCALE = 4;
    private static final BigDecimal SPREAD_RATE = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String QUOTE_CURRENCY = "TRY";
    public YahooQuoteDto toQuoteDto(YahooChartResponse.Meta meta) {
        return new YahooQuoteDto(meta.regularMarketPrice(), meta.previousClose());
    }
    public TcmbRateDto toRateDto(Element el) {
        return new TcmbRateDto(
                el.getAttribute("Kod"),
                text(el, "CurrencyName"),
                text(el, "Isim"),
                toInt(text(el, "Unit"), 1),
                toBigDecimal(text(el, "ForexBuying")),
                toBigDecimal(text(el, "ForexSelling")),
                toBigDecimal(text(el, "BanknoteBuying")),
                toBigDecimal(text(el, "BanknoteSelling")),
                toBigDecimal(text(el, "CrossRateUSD")),
                toBigDecimal(text(el, "CrossRateOther")));
    }
    public String toCurrencyPairCode(String rawCurrencyCode) {
        return rawCurrencyCode + QUOTE_CURRENCY;
    }
    public Forex toEntity(TcmbRateDto dto) {
        int unit = dto.unit();
        return Forex.builder()
                .currencyCode(toCurrencyPairCode(dto.currencyCode()))
                .currencyName(dto.currencyName())
                .currencyNameTr(dto.currencyNameTr())
                .unit(unit)
                .forexBuying(divideByUnit(dto.forexBuying(), unit))
                .forexSelling(divideByUnit(dto.forexSelling(), unit))
                .banknoteBuying(divideByUnit(dto.banknoteBuying(), unit))
                .banknoteSelling(divideByUnit(dto.banknoteSelling(), unit))
                .crossRateUsd(divideByUnit(dto.crossRateUsd(), unit))
                .crossRateOther(dto.crossRateOther())
                .tcmbUpdatedAt(LocalDateTime.now())
                .build();
    }
    public void updateEntity(Forex existing, TcmbRateDto dto) {
        int unit = dto.unit();
        existing.setCurrencyName(dto.currencyName());
        existing.setCurrencyNameTr(dto.currencyNameTr());
        existing.setUnit(unit);
        existing.setForexBuying(divideByUnit(dto.forexBuying(), unit));
        existing.setForexSelling(divideByUnit(dto.forexSelling(), unit));
        existing.setBanknoteBuying(divideByUnit(dto.banknoteBuying(), unit));
        existing.setBanknoteSelling(divideByUnit(dto.banknoteSelling(), unit));
        existing.setCrossRateUsd(divideByUnit(dto.crossRateUsd(), unit));
        existing.setCrossRateOther(dto.crossRateOther());
        existing.setTcmbUpdatedAt(LocalDateTime.now());
    }
    public void applyYahooSnapshot(Forex forex, YahooQuoteDto quote, LocalDateTime now) {
        BigDecimal currentPrice = quote.regularMarketPrice();
        if (currentPrice == null) {
            return;
        }
        forex.setCurrentPrice(scale(currentPrice));
        forex.setSellingPrice(scale(currentPrice.multiply(BigDecimal.ONE.add(SPREAD_RATE))));
        applyChangeFields(forex, currentPrice, quote.previousClose());
        forex.setYahooUpdatedAt(now);
    }
    public void applySyntheticSnapshot(Forex forex, YahooQuoteDto pairQuote,
                                       BigDecimal usdtryPrice, BigDecimal usdtryChange,
                                       boolean isUsdBase, LocalDateTime now) {
        BigDecimal pairPrice = pairQuote.regularMarketPrice();
        if (pairPrice == null || pairPrice.signum() == 0) {
            return;
        }
        BigDecimal syntheticPrice = isUsdBase
                ? safeDivide(usdtryPrice, pairPrice)
                : safeMultiply(usdtryPrice, pairPrice);
        if (syntheticPrice == null) {
            return;
        }
        forex.setCurrentPrice(syntheticPrice);
        forex.setSellingPrice(scale(syntheticPrice.multiply(BigDecimal.ONE.add(SPREAD_RATE))));
        BigDecimal pairPreviousClose = pairQuote.previousClose();
        if (pairPreviousClose != null && pairPreviousClose.signum() > 0) {
            BigDecimal usdtryPreviousClose = usdtryChange != null
                    ? usdtryPrice.subtract(usdtryChange)
                    : usdtryPrice;
            if (usdtryPreviousClose.signum() > 0) {
                BigDecimal syntheticPreviousClose = isUsdBase
                        ? safeDivide(usdtryPreviousClose, pairPreviousClose)
                        : safeMultiply(usdtryPreviousClose, pairPreviousClose);
                applyChangeFields(forex, syntheticPrice, syntheticPreviousClose);
            }
        }
        forex.setYahooUpdatedAt(now);
    }
    public ForexCandle toCandleEntity(YahooCandleDto dto, String currencyCode, Forex forex) {
        BigDecimal o = scale(dto.open());
        BigDecimal h = scale(dto.high());
        BigDecimal l = scale(dto.low());
        BigDecimal c = scale(dto.close());
        return ForexCandle.builder()
                .currencyCode(currencyCode)
                .forex(forex)
                .candleDate(dto.candleDate())
                .open(o)
                .high(maxOf(o, h, c))
                .low(minOf(o, l, c))
                .close(c)
                .build();
    }
    public void updateCandleEntity(ForexCandle existing, YahooCandleDto dto) {
        BigDecimal o = scale(dto.open());
        BigDecimal h = scale(dto.high());
        BigDecimal l = scale(dto.low());
        BigDecimal c = scale(dto.close());
        existing.setOpen(o);
        existing.setHigh(maxOf(o, h, c));
        existing.setLow(minOf(o, l, c));
        existing.setClose(c);
    }
    public List<YahooCandleDto> buildSyntheticCandles(List<YahooCandleDto> pairCandles,
                                                      Map<String, ForexCandle> usdtryCandleByDate,
                                                      BigDecimal fallbackPrice,
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
        BigDecimal low  = safeDivide(usdtry.getLow(),  pair.high());  
        BigDecimal close = safeDivide(usdtry.getClose(), pair.close());
        if (open == null || high == null || low == null || close == null) {
            return null;
        }
        high = maxOf(open, high, close);
        low  = minOf(open, low, close);
        return new YahooCandleDto(pair.candleDate(), open, high, low, close, pair.volume());
    }
    private YahooCandleDto buildNonUsdBaseCandle(YahooCandleDto pair, ForexCandle usdtry) {
        BigDecimal open = safeMultiply(usdtry.getOpen(), pair.open());
        BigDecimal high = safeMultiply(usdtry.getHigh(), pair.high());
        BigDecimal low  = safeMultiply(usdtry.getLow(),  pair.low());
        BigDecimal close = safeMultiply(usdtry.getClose(), pair.close());
        if (open == null || high == null || low == null || close == null) {
            return null;
        }
        high = maxOf(open, high, close);
        low  = minOf(open, low, close);
        return new YahooCandleDto(pair.candleDate(), open, high, low, close, pair.volume());
    }
    private void applyChangeFields(Forex forex, BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            forex.setChange24h(null);
            forex.setChangePercent24h(null);
            return;
        }
        BigDecimal change = current.subtract(previous);
        BigDecimal changePercent = change
                .divide(previous, SCALE + 2, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(SCALE, RoundingMode.HALF_UP);
        forex.setChange24h(scale(change));
        forex.setChangePercent24h(changePercent);
    }
    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }
    private BigDecimal safeMultiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return null;
        }
        return a.multiply(b).setScale(SCALE, RoundingMode.HALF_UP);
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
    private BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(SCALE, RoundingMode.HALF_UP) : null;
    }
    private BigDecimal divideByUnit(BigDecimal value, int unit) {
        if (value == null || unit <= 1) {
            return value;
        }
        return value.divide(new BigDecimal(unit), SCALE, RoundingMode.HALF_UP);
    }
    private static int toInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return defaultValue; }
    }
    private static BigDecimal toBigDecimal(String value) {
        if (value == null) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException e) { return null; }
    }
    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        String value = list.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }
}
