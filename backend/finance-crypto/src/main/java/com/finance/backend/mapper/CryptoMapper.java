package com.finance.backend.mapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoMarketDto;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Component
public class CryptoMapper {
    private static final int SCALE = 4;
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    public List<CoinGeckoMarketDto> toMarketDtos(JsonNode markets) {
        List<CoinGeckoMarketDto> result = new ArrayList<>();
        for (JsonNode market : markets) {
            result.add(new CoinGeckoMarketDto(
                    market.get("id").asText(),
                    market.get("symbol").asText().toUpperCase(),
                    market.get("name").asText(),
                    market.get("image").asText(),
                    toDecimal(market.get("current_price")),
                    toDecimal(market.get("price_change_24h")),
                    toDecimal(market.get("price_change_percentage_24h")),
                    market.has("market_cap") ? toDecimal(market.get("market_cap")) : null,
                    market.has("total_volume") ? toDecimal(market.get("total_volume")) : null
            ));
        }
        return result;
    }
    public List<CoinGeckoCandleDto> toCandleDtosFromRange(JsonNode jsonData, String coinId) {
        JsonNode pricesArray = jsonData.get("prices");
        JsonNode volumesArray = jsonData.get("total_volumes");
        if (pricesArray == null || !pricesArray.isArray()) {
            return Collections.emptyList();
        }
        Map<String, Long> volumeMap = buildVolumeMap(volumesArray);
        List<CoinGeckoCandleDto> candles = new ArrayList<>();
        BigDecimal previousClose = null;
        for (JsonNode pricePoint : pricesArray) {
            long timestamp = pricePoint.get(0).asLong();
            BigDecimal price = pricePoint.get(1).decimalValue();
            LocalDateTime candleDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), ISTANBUL_ZONE)
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
    public List<CoinGeckoCandleDto> toCandleDtoFromOhlc(JsonNode ohlcData, String coinId) {
        if (ohlcData == null || !ohlcData.isArray() || ohlcData.isEmpty()) {
            return Collections.emptyList();
        }
        BigDecimal open = ohlcData.get(0).get(1).decimalValue();
        BigDecimal close = ohlcData.get(ohlcData.size() - 1).get(4).decimalValue();
        BigDecimal high = open;
        BigDecimal low = open;
        for (JsonNode candle : ohlcData) {
            BigDecimal h = candle.get(2).decimalValue();
            BigDecimal l = candle.get(3).decimalValue();
            if (h.compareTo(high) > 0) high = h;
            if (l.compareTo(low) < 0) low = l;
        }
        if (close.compareTo(high) > 0) high = close;
        if (close.compareTo(low) < 0) low = close;
        LocalDateTime candleDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        return List.of(new CoinGeckoCandleDto(coinId, candleDate, open, high, low, close, null));
    }
    private Map<String, Long> buildVolumeMap(JsonNode volumesArray) {
        Map<String, Long> volumeMap = new HashMap<>();
        if (volumesArray == null || !volumesArray.isArray()) {
            return volumeMap;
        }
        for (JsonNode volumePoint : volumesArray) {
            if (volumePoint.size() < 2) continue;
            JsonNode tsNode = volumePoint.get(0);
            JsonNode volNode = volumePoint.get(1);
            if (tsNode == null || tsNode.isNull() || volNode == null || volNode.isNull()) continue;
            long timestamp = tsNode.asLong();
            long volume = volNode.asLong();
            LocalDateTime volumeDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.DAYS);
            String dateKey = volumeDate.toLocalDate().toString();
            volumeMap.merge(dateKey, volume, (a, b) -> Math.max(a, b));
        }
        return volumeMap;
    }
    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.decimalValue();
    }
    public Crypto toEntity(CoinGeckoMarketDto usdDto, BigDecimal tryPrice, LocalDateTime now) {
        return Crypto.builder()
                .id(usdDto.id())
                .symbol(usdDto.symbol())
                .name(usdDto.name())
                .image(usdDto.image())
                .currentPrice(scale(usdDto.currentPrice()))
                .currentPriceTry(tryPrice != null ? scale(tryPrice) : null)
                .changeAmount(scale(usdDto.priceChange24h()))
                .changePercent(scale(usdDto.priceChangePercentage24h()))
                .marketCap(scale(usdDto.marketCap()))
                .totalVolume(scale(usdDto.totalVolume()))
                .exchange("CoinGecko")
                .currency("USD")
                .lastUpdated(now)
                .build();
    }
    public void updateEntityFromDto(Crypto existing, CoinGeckoMarketDto usdDto,
                                    BigDecimal tryPrice, LocalDateTime now) {
        existing.setSymbol(usdDto.symbol());
        existing.setName(usdDto.name());
        existing.setImage(usdDto.image());
        existing.setCurrentPrice(scale(usdDto.currentPrice()));
        existing.setCurrentPriceTry(tryPrice != null ? scale(tryPrice) : null);
        existing.setChangeAmount(scale(usdDto.priceChange24h()));
        existing.setChangePercent(scale(usdDto.priceChangePercentage24h()));
        existing.setMarketCap(scale(usdDto.marketCap()));
        existing.setTotalVolume(scale(usdDto.totalVolume()));
        existing.setLastUpdated(now);
    }
    public CryptoCandle toCandleEntity(CoinGeckoCandleDto dto, Crypto crypto) {
        return CryptoCandle.builder()
                .crypto(crypto)
                .cryptoId(dto.coinId())
                .candleDate(dto.candleDate().truncatedTo(ChronoUnit.DAYS))
                .open(scale(dto.open()))
                .high(scale(dto.high()))
                .low(scale(dto.low()))
                .close(scale(dto.close()))
                .volume(dto.volume())
                .build();
    }
    public void updateCandleEntity(CryptoCandle existing, CoinGeckoCandleDto dto) {
        existing.setOpen(scale(dto.open()));
        existing.setHigh(scale(dto.high()));
        existing.setLow(scale(dto.low()));
        existing.setClose(scale(dto.close()));
        existing.setVolume(dto.volume());
    }
    private BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(SCALE, RoundingMode.HALF_UP) : null;
    }
}
