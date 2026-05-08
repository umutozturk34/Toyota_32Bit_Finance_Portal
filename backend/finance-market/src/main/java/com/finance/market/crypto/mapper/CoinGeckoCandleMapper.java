package com.finance.market.crypto.mapper;
import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.common.config.AppProperties;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.market.crypto.dto.internal.BinanceKlineResponse;
import com.finance.market.crypto.dto.internal.CoinGeckoMarketDto;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class CoinGeckoCandleMapper {

    @Autowired
    protected AppProperties appProperties;

    @Mapping(source = "currentPrice", target = "currentPrice")
    @Mapping(source = "priceChange24h", target = "priceChange24h")
    @Mapping(source = "priceChangePercentage24h", target = "priceChangePercentage24h")
    @Mapping(source = "marketCap", target = "marketCap")
    @Mapping(source = "totalVolume", target = "totalVolume")
    public abstract CoinGeckoSnapshotDto toSnapshotDto(CoinGeckoMarketDto dto);

    public List<CoinGeckoSnapshotDto> toSnapshotDtos(List<CoinGeckoMarketDto> markets) {
        if (markets == null || markets.isEmpty()) {
            return Collections.emptyList();
        }
        return markets.stream().map(this::toSnapshotDto).toList();
    }

    @Mapping(source = "kline.open", target = "open")
    @Mapping(source = "kline.high", target = "high")
    @Mapping(source = "kline.low", target = "low")
    @Mapping(source = "kline.close", target = "close")
    @Mapping(target = "candleDate", expression = "java(toLocalDateTime(kline.openTime()))")
    @Mapping(target = "volume", expression = "java(kline.volume() != null ? kline.volume().setScale(0, java.math.RoundingMode.HALF_UP).longValue() : null)")
    public abstract CoinGeckoCandleDto toCandleDto(BinanceKlineResponse kline, String coinId);

    public List<CoinGeckoCandleDto> toCandleDtos(List<BinanceKlineResponse> responses, String coinId) {
        if (responses == null || responses.isEmpty()) {
            return Collections.emptyList();
        }
        return responses.stream().map(r -> toCandleDto(r, coinId)).toList();
    }

    protected LocalDateTime toLocalDateTime(Long epochMillis) {
        ZoneId zone = ZoneId.of(appProperties.getTimezone());
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
    }
}
