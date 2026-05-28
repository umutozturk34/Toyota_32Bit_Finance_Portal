package com.finance.market.forex.mapper;

import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.mapper.MarketMetadataBuilder;
import com.finance.market.forex.dto.response.ForexCandleResponse;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.shared.dto.response.ForexMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * MapStruct mapper from forex entities/candles to API responses. Forex has no true OHLC, so every
 * candle's open/high/low/close are mapped from the selling price; buying/effective rates ride along
 * as extra fields and in the asset metadata.
 */
@Mapper(componentModel = "spring")
public abstract class ForexResponseMapper implements MarketMetadataBuilder<Forex, ForexMetadata> {

    @Mapping(target = "candleDate", source = "candleDate")
    @Mapping(target = "open", source = "sellingPrice")
    @Mapping(target = "high", source = "sellingPrice")
    @Mapping(target = "low", source = "sellingPrice")
    @Mapping(target = "close", source = "sellingPrice")
    @Mapping(target = "sellingPrice", source = "sellingPrice")
    @Mapping(target = "buyingPrice", source = "buyingPrice")
    @Mapping(target = "effectiveBuyingPrice", source = "effectiveBuyingPrice")
    @Mapping(target = "effectiveSellingPrice", source = "effectiveSellingPrice")
    public abstract ForexCandleResponse toForexCandleResponse(ForexCandle candle);

    public abstract List<ForexCandleResponse> toForexCandleResponses(List<ForexCandle> candles);

    @Mapping(target = "code", source = "currencyCode")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "price", source = "sellingPrice")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "lastUpdated", source = "lastUpdated")
    @Mapping(target = "type", expression = "java(MarketType.FOREX)")
    @Mapping(target = "image", source = "image")
    @Mapping(target = "metadata", source = "forex", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Forex forex);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Forex> forexList);

    @Override
    @Named("metadata")
    public ForexMetadata buildMetadata(Forex forex) {
        return new ForexMetadata(
                forex.getBuyingPrice(),
                forex.getSellingPrice(),
                forex.getEffectiveBuyingPrice(),
                forex.getEffectiveSellingPrice(),
                forex.isTradable()
        );
    }
}
