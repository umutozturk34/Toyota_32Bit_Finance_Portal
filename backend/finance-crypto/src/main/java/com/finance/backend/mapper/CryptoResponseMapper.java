package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.CryptoResponse;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class CryptoResponseMapper {

    public abstract CryptoResponse toCryptoResponse(Crypto crypto);

    public abstract CandleResponse toCandleResponse(CryptoCandle candle);

    public abstract List<CandleResponse> toCryptoCandleResponses(List<CryptoCandle> candles);
}
