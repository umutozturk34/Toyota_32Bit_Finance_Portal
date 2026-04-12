package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.ForexResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class ForexResponseMapper {

    public abstract ForexResponse toForexResponse(Forex forex);

    public abstract List<ForexResponse> toForexResponses(List<Forex> forexList);

    @Mapping(target = "volume", ignore = true)
    public abstract CandleResponse toCandleResponse(ForexCandle candle);

    public abstract List<CandleResponse> toForexCandleResponses(List<ForexCandle> candles);

    @Mapping(target = "code", source = "currencyCode")
    @Mapping(target = "name", source = "currencyName")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "change24h")
    @Mapping(target = "changePercent", source = "changePercent24h")
    @Mapping(target = "type", expression = "java(MarketType.FOREX)")
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "metadata", source = "forex", qualifiedByName = "forexMetadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Forex forex);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Forex> forexList);

    @Named("forexMetadata")
    protected Map<String, Object> buildForexMetadata(Forex forex) {
        Map<String, Object> metadata = new HashMap<>();
        if (forex.getSellingPrice() != null) {
            metadata.put("sellingPrice", forex.getSellingPrice());
        }
        if (forex.getForexBuying() != null) {
            metadata.put("forexBuying", forex.getForexBuying());
        }
        if (forex.getForexSelling() != null) {
            metadata.put("forexSelling", forex.getForexSelling());
        }
        if (forex.getUnit() != null) {
            metadata.put("unit", forex.getUnit());
        }
        if (forex.getBanknoteBuying() != null) {
            metadata.put("banknoteBuying", forex.getBanknoteBuying());
        }
        if (forex.getBanknoteSelling() != null) {
            metadata.put("banknoteSelling", forex.getBanknoteSelling());
        }
        if (forex.getYahooUpdatedAt() != null) {
            metadata.put("yahooUpdatedAt", forex.getYahooUpdatedAt());
        }
        if (forex.getTcmbUpdatedAt() != null) {
            metadata.put("tcmbUpdatedAt", forex.getTcmbUpdatedAt());
        }
        return metadata;
    }
}
