package com.finance.market.crypto.mapper;

import com.finance.market.core.mapper.MarketMetadataBuilder;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.shared.dto.response.CryptoMetadata;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.model.CryptoCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/** MapStruct mapper from crypto entities/candles to API responses; price is the TRY value. */
@Mapper(componentModel = "spring")
public abstract class CryptoResponseMapper implements MarketMetadataBuilder<Crypto, CryptoMetadata> {

    public abstract List<CandleResponse> toCryptoCandleResponses(List<CryptoCandle> candles);

    @Mapping(target = "code", source = "id")
    @Mapping(target = "price", source = "currentPriceTry")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.CRYPTO)")
    @Mapping(target = "metadata", source = "crypto", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Crypto crypto);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Crypto> cryptos);

    @Override
    @Named("metadata")
    public CryptoMetadata buildMetadata(Crypto crypto) {
        return new CryptoMetadata(
                crypto.getMarketCap(),
                crypto.getTotalVolume(),
                crypto.getSymbol(),
                crypto.getCurrentPrice()
        );
    }
}
