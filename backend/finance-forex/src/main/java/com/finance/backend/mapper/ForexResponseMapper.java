package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.ForexMetadata;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ForexResponseMapper {

    public abstract List<CandleResponse> toForexCandleResponses(List<ForexCandle> candles);

    @Mapping(target = "code", source = "currencyCode")
    @Mapping(target = "name", source = "currencyNameTr")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "change24h")
    @Mapping(target = "changePercent", source = "changePercent24h")
    @Mapping(target = "type", expression = "java(MarketType.FOREX)")
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "metadata", source = "forex", qualifiedByName = "forexMetadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Forex forex);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Forex> forexList);

    @Named("forexMetadata")
    protected ForexMetadata buildForexMetadata(Forex forex) {
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
