package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.CryptoMetadata;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

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
    protected CryptoMetadata buildCryptoMetadata(Crypto crypto) {
        return new CryptoMetadata(
                crypto.getMarketCap(),
                crypto.getTotalVolume(),
                crypto.getSymbol(),
                crypto.getCurrentPrice()
        );
    }
}
