package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.CryptoResponse;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CryptoResponseMapper {

    public CryptoResponse toCryptoResponse(Crypto c) {
        return new CryptoResponse(
                c.getId(),
                c.getSymbol(),
                c.getName(),
                c.getCurrentPrice(),
                c.getCurrentPriceTry(),
                c.getChangeAmount(),
                c.getChangePercent(),
                c.getMarketCap(),
                c.getTotalVolume(),
                c.getLastUpdated()
        );
    }

    public CandleResponse toCandleResponse(CryptoCandle c) {
        return new CandleResponse(
                c.getCandleDate(),
                c.getOpen(),
                c.getHigh(),
                c.getLow(),
                c.getClose(),
                c.getVolume()
        );
    }

    public List<CandleResponse> toCryptoCandleResponses(List<CryptoCandle> candles) {
        return candles.stream().map(this::toCandleResponse).toList();
    }
}
