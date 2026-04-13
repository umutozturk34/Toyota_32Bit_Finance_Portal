package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class CryptoResponseMapper {

    public abstract List<CandleResponse> toCryptoCandleResponses(List<CryptoCandle> candles);

    @Mapping(target = "code", source = "id")
    @Mapping(target = "price", source = "currentPriceTry")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.CRYPTO)")
    @Mapping(target = "metadata", source = "crypto", qualifiedByName = "cryptoMetadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Crypto crypto);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Crypto> cryptos);

    @Named("cryptoMetadata")
    protected Map<String, Object> buildCryptoMetadata(Crypto crypto) {
        Map<String, Object> metadata = new HashMap<>();
        if (crypto.getMarketCap() != null) {
            metadata.put("marketCap", crypto.getMarketCap());
        }
        if (crypto.getTotalVolume() != null) {
            metadata.put("totalVolume", crypto.getTotalVolume());
        }
        if (crypto.getSymbol() != null) {
            metadata.put("symbol", crypto.getSymbol());
        }
        if (crypto.getCurrentPrice() != null) {
            metadata.put("currentPriceUsd", crypto.getCurrentPrice());
        }
        return metadata;
    }
}
