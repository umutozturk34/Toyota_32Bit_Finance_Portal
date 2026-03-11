package com.finance.backend.mapper;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoSnapshotDto;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class CryptoMapper {

    @Autowired
    protected AppProperties appProperties;

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(source = "usdDto.priceChange24h", target = "changeAmount")
    @Mapping(source = "usdDto.priceChangePercentage24h", target = "changePercent")
    @Mapping(target = "symbol", expression = "java(usdDto.symbol().toUpperCase())")
    @Mapping(target = "currentPriceTry", expression = "java(tryPrice)")
    @Mapping(target = "exchange", constant = "CoinGecko")
    @Mapping(target = "currency", constant = "USD")
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract Crypto toEntity(CoinGeckoSnapshotDto usdDto, BigDecimal tryPrice, LocalDateTime now);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(source = "usdDto.priceChange24h", target = "changeAmount")
    @Mapping(source = "usdDto.priceChangePercentage24h", target = "changePercent")
    @Mapping(target = "currentPriceTry", expression = "java(tryPrice)")
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract void updateEntityFromDto(@MappingTarget Crypto existing,
                                             CoinGeckoSnapshotDto usdDto,
                                             BigDecimal tryPrice, LocalDateTime now);

    @AfterMapping
    void enrichCrypto(@MappingTarget Crypto crypto) {
        crypto.scaleFields(appProperties.getScale());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "dto.coinId", target = "cryptoId")
    public abstract CryptoCandle toCandleEntity(CoinGeckoCandleDto dto, Crypto crypto);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract void updateCandleEntity(@MappingTarget CryptoCandle existing, CoinGeckoCandleDto dto);

    @AfterMapping
    void enrichCryptoCandle(@MappingTarget CryptoCandle candle) {
        candle.scaleOhlc(appProperties.getScale());
    }
}
