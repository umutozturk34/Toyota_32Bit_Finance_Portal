package com.finance.backend.mapper;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoSnapshotDto;
import com.finance.backend.dto.internal.CoinGeckoMarketChartResponse;
import com.finance.backend.dto.internal.CoinGeckoMarketDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class CoinGeckoCandleMapper {

    private final ZoneId appZone;

    public CoinGeckoCandleMapper(AppProperties appProperties) {
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    public CoinGeckoSnapshotDto toSnapshotDto(CoinGeckoMarketDto dto) {
        return new CoinGeckoSnapshotDto(
                dto.id(), dto.symbol(), dto.name(), dto.image(),
                dto.currentPrice(), dto.priceChange24h(),
                dto.priceChangePercentage24h(), dto.marketCap(), dto.totalVolume()
        );
    }

    public List<CoinGeckoSnapshotDto> toSnapshotDtos(List<CoinGeckoMarketDto> markets) {
        if (markets == null || markets.isEmpty()) {
            return Collections.emptyList();
        }
        return markets.stream().map(this::toSnapshotDto).toList();
    }

    public List<CoinGeckoCandleDto> toCandleDtosFromChart(CoinGeckoMarketChartResponse chart, String coinId) {
        if (chart.prices() == null || chart.prices().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Long> volumeMap = buildVolumeMap(chart.totalVolumes());
        List<CoinGeckoCandleDto> candles = new ArrayList<>();
        BigDecimal previousClose = null;

        for (List<BigDecimal> pricePoint : chart.prices()) {
            long timestamp = pricePoint.get(0).longValue();
            BigDecimal price = pricePoint.get(1);

            LocalDateTime candleDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), appZone)
                    .truncatedTo(ChronoUnit.DAYS);

            BigDecimal open, high, low, close;
            if (previousClose == null) {
                open = high = low = close = price;
            } else {
                open = previousClose;
                close = price;
                high = open.max(close);
                low = open.min(close);
            }

            String dateKey = candleDate.toLocalDate().toString();
            Long volume = volumeMap.get(dateKey);
            candles.add(new CoinGeckoCandleDto(coinId, candleDate, open, high, low, close, volume));
            previousClose = close;
        }
        return candles;
    }

    public List<CoinGeckoCandleDto> toCandleDtosFromOhlc(List<List<BigDecimal>> ohlcData, String coinId) {
        if (ohlcData == null || ohlcData.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal open = ohlcData.getFirst().get(1);
        BigDecimal close = ohlcData.getLast().get(4);
        BigDecimal high = open;
        BigDecimal low = open;

        for (List<BigDecimal> candle : ohlcData) {
            BigDecimal h = candle.get(2);
            BigDecimal l = candle.get(3);
            if (h.compareTo(high) > 0) high = h;
            if (l.compareTo(low) < 0) low = l;
        }
        if (close.compareTo(high) > 0) high = close;
        if (close.compareTo(low) < 0) low = close;

        LocalDateTime candleDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        return List.of(new CoinGeckoCandleDto(coinId, candleDate, open, high, low, close, null));
    }

    private Map<String, Long> buildVolumeMap(List<List<BigDecimal>> totalVolumes) {
        Map<String, Long> volumeMap = new HashMap<>();
        if (totalVolumes == null) return volumeMap;

        for (List<BigDecimal> volumePoint : totalVolumes) {
            if (volumePoint.size() < 2 || volumePoint.get(0) == null || volumePoint.get(1) == null) continue;
            long timestamp = volumePoint.get(0).longValue();
            long volume = volumePoint.get(1).longValue();
            LocalDateTime volumeDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), appZone)
                    .truncatedTo(ChronoUnit.DAYS);
            String dateKey = volumeDate.toLocalDate().toString();
            volumeMap.merge(dateKey, volume, (a, b) -> Math.max(a, b));
        }
        return volumeMap;
    }
}
