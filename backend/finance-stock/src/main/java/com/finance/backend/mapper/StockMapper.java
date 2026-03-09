package com.finance.backend.mapper;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.dto.internal.YahooChartResponse.Meta;
import com.finance.backend.dto.internal.YahooChartResponse.Quote;
import com.finance.backend.dto.internal.YahooChartResponse.Result;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;
@Component
public class StockMapper {
    private static final int SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    public YahooStockQuoteDto toQuoteDto(Result result, String symbol) {
        Meta meta = result.meta();
        Quote quote = result.firstQuote();
        BigDecimal openPrice = (quote != null && quote.open() != null && !quote.open().isEmpty())
                ? quote.open().getFirst() : null;
        return new YahooStockQuoteDto(
                symbol,
                Objects.toString(meta.longName(), Objects.toString(meta.shortName(), symbol)),
                meta.regularMarketPrice(),
                meta.chartPreviousClose(),
                openPrice,
                meta.dayHigh(),
                meta.dayLow(),
                meta.volume() != null ? meta.volume() : 0L,
                Objects.toString(meta.exchangeName(), "BIST"),
                Objects.toString(meta.currency(), "TRY")
        );
    }
    public Stock toEntity(YahooStockQuoteDto dto, LocalDateTime now) {
        return Stock.builder()
                .symbol(dto.symbol())
                .name(dto.name())
                .currentPrice(scale(dto.currentPrice()))
                .previousClose(scale(dto.previousClose()))
                .openPrice(scale(dto.openPrice()))
                .dayHigh(scale(dto.dayHigh()))
                .dayLow(scale(dto.dayLow()))
                .volume(dto.volume())
                .priceChangeAmount(calcChange(dto.currentPrice(), dto.previousClose()))
                .priceChangePercent(calcChangePercent(dto.currentPrice(), dto.previousClose()))
                .exchange(dto.exchange())
                .currency(dto.currency())
                .lastUpdated(now)
                .build();
    }
    public void updateEntityFromDto(Stock existing, YahooStockQuoteDto dto, LocalDateTime now) {
        existing.setName(dto.name());
        existing.setCurrentPrice(scale(dto.currentPrice()));
        existing.setPreviousClose(scale(dto.previousClose()));
        existing.setOpenPrice(scale(dto.openPrice()));
        existing.setDayHigh(scale(dto.dayHigh()));
        existing.setDayLow(scale(dto.dayLow()));
        existing.setVolume(dto.volume());
        existing.setPriceChangeAmount(calcChange(dto.currentPrice(), dto.previousClose()));
        existing.setPriceChangePercent(calcChangePercent(dto.currentPrice(), dto.previousClose()));
        existing.setExchange(dto.exchange());
        existing.setCurrency(dto.currency());
        existing.setLastUpdated(now);
    }
    public StockCandle toCandleEntity(YahooCandleDto dto, Stock stock) {
        return StockCandle.builder()
                .stock(stock)
                .stockSymbol(stock.getSymbol())
                .candleDate(dto.candleDate())
                .open(scale(dto.open()))
                .high(scale(dto.high()))
                .low(scale(dto.low()))
                .close(scale(dto.close()))
                .volume(dto.volume())
                .build();
    }
    public void updateCandleEntity(StockCandle existing, YahooCandleDto dto) {
        existing.setOpen(scale(dto.open()));
        existing.setHigh(scale(dto.high()));
        existing.setLow(scale(dto.low()));
        existing.setClose(scale(dto.close()));
        existing.setVolume(dto.volume());
    }
    private BigDecimal calcChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).setScale(SCALE, RoundingMode.HALF_UP);
    }
    private BigDecimal calcChangePercent(BigDecimal current, BigDecimal previous) {
        BigDecimal change = calcChange(current, previous);
        if (change == null) {
            return null;
        }
        return change.divide(previous, SCALE + 2, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
    private BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(SCALE, RoundingMode.HALF_UP) : null;
    }
}
