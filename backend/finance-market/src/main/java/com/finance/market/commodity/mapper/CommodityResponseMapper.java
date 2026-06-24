package com.finance.market.commodity.mapper;

import com.finance.market.core.mapper.MarketMetadataBuilder;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.shared.dto.response.CommodityMetadata;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * MapStruct mapper that exposes commodity entities through the unified market API surface.
 * It maps {@link Commodity} to {@link MarketAssetResponse} (the generic asset envelope) and
 * {@link CommodityCandle} history to {@link CandleResponse}, and supplies the commodity-specific
 * {@link CommodityMetadata} via the {@link MarketMetadataBuilder} contract.
 */
@Mapper(componentModel = "spring")
public abstract class CommodityResponseMapper implements MarketMetadataBuilder<Commodity, CommodityMetadata> {

    /**
     * Maps a commodity entity to the unified market-asset response, renaming code/name/price
     * fields, tagging the asset {@code type} as {@code COMMODITY}, and nesting commodity-specific
     * fields under {@code metadata}.
     */
    @Mapping(target = "code", source = "commodityCode")
    @Mapping(target = "name", source = "commodityNameTr")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "lastUpdated", source = "yahooUpdatedAt")
    @Mapping(target = "type", expression = "java(MarketType.COMMODITY)")
    @Mapping(target = "metadata", source = "commodity", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Commodity commodity);

    /** Maps a list of commodities to unified market-asset responses, preserving order. */
    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Commodity> commodities);

    /** Maps commodity OHLC history to generic candle responses, preserving order. */
    public abstract List<CandleResponse> toCommodityCandleResponses(List<CommodityCandle> candles);

    /** Single OHLC candle → generic response; commodities carry no volume, so it is left unset. */
    @Mapping(target = "volume", ignore = true)
    public abstract CandleResponse toCommodityCandleResponse(CommodityCandle candle);

    /**
     * Builds the commodity-specific metadata block (USD prices, unit, OHLC and volume) nested
     * inside the unified asset response. Registered under the {@code "metadata"} qualifier so the
     * mapping methods above can reference it.
     */
    @Override
    @Named("metadata")
    public CommodityMetadata buildMetadata(Commodity commodity) {
        return new CommodityMetadata(
                commodity.getCurrentPriceUsd(),
                commodity.getPreviousPriceUsd(),
                commodity.getUnit(),
                commodity.getOpenPrice(),
                commodity.getDayHigh(),
                commodity.getDayLow(),
                commodity.getVolume()
        );
    }
}
