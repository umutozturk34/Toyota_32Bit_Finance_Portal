package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.CommodityMetadata;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class CommodityResponseMapper implements MarketMetadataBuilder<Commodity, CommodityMetadata> {

    @Mapping(target = "code", source = "commodityCode")
    @Mapping(target = "name", source = "commodityNameTr")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "lastUpdated", source = "yahooUpdatedAt")
    @Mapping(target = "type", expression = "java(MarketType.COMMODITY)")
    @Mapping(target = "metadata", source = "commodity", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Commodity commodity);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Commodity> commodities);

    public abstract List<CandleResponse> toCommodityCandleResponses(List<CommodityCandle> candles);

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
