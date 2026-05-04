package com.finance.crypto.mapper;
import com.finance.common.mapper.BaseMarketMapper;

import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.crypto.model.Crypto;
import com.finance.crypto.model.CryptoCandle;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class CryptoMapper extends BaseMarketMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(source = "usdDto.priceChange24h", target = "changeAmount")
    @Mapping(source = "usdDto.priceChangePercentage24h", target = "changePercent")
    @Mapping(target = "symbol", expression = "java(usdDto.symbol().toUpperCase())")
    @Mapping(source = "usdDto.image", target = "image")
    @Mapping(source = "usdDto.name", target = "name")
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
    @Mapping(source = "usdDto.image", target = "image")
    @Mapping(source = "usdDto.name", target = "name")
    @Mapping(target = "currentPriceTry", expression = "java(tryPrice)")
    @Mapping(target = "lastUpdated", expression = "java(now)")
    public abstract void updateEntityFromDto(@MappingTarget Crypto existing,
                                             CoinGeckoSnapshotDto usdDto,
                                             BigDecimal tryPrice, LocalDateTime now);

    @AfterMapping
    void enrichCrypto(@MappingTarget Crypto crypto) {
        crypto.scaleFields(scale());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "dto.coinId", target = "cryptoId")
    public abstract CryptoCandle toCandleEntity(CoinGeckoCandleDto dto, Crypto crypto);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract void updateCandleEntity(@MappingTarget CryptoCandle existing, CoinGeckoCandleDto dto);

    @AfterMapping
    void enrichCryptoCandle(@MappingTarget CryptoCandle candle) {
        candle.scaleFields(scale());
    }
}
