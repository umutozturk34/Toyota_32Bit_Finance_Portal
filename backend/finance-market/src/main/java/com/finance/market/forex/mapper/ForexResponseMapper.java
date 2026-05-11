package com.finance.market.forex.mapper;

import com.finance.market.core.mapper.MarketMetadataBuilder;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.shared.dto.response.ForexMetadata;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ForexResponseMapper implements MarketMetadataBuilder<Forex, ForexMetadata> {

    public abstract List<CandleResponse> toForexCandleResponses(List<ForexCandle> candles);

    @Mapping(target = "code", source = "currencyCode")
    @Mapping(target = "name", source = "currencyNameTr")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.FOREX)")
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "metadata", source = "forex", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Forex forex);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Forex> forexList);

    @Override
    @Named("metadata")
    public ForexMetadata buildMetadata(Forex forex) {
        return new ForexMetadata(
                forex.getSellingPrice(),
                forex.getForexBuying(),
                forex.getForexSelling(),
                forex.getUnit(),
                forex.getBanknoteBuying(),
                forex.getBanknoteSelling(),
                forex.getYahooUpdatedAt(),
                forex.getTcmbUpdatedAt(),
                forex.getOpenPrice(),
                forex.getDayHigh(),
                forex.getDayLow(),
                forex.getVolume()
        );
    }
}
