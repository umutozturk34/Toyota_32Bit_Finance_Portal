package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.CryptoResponse;
import com.finance.backend.dto.response.ForexResponse;
import com.finance.backend.dto.response.StockResponse;
import com.finance.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResponseMapper {

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

    public ForexResponse toForexResponse(Forex f) {
        return new ForexResponse(
                f.getCurrencyCode(),
                f.getCurrencyName(),
                f.getCurrentPrice(),
                f.getSellingPrice(),
                f.getChange24h(),
                f.getChangePercent24h(),
                f.getForexBuying(),
                f.getForexSelling(),
                f.getBanknoteBuying(),
                f.getBanknoteSelling(),
                f.getUpdatedAt(),
                f.getTcmbUpdatedAt(),
                f.getYahooUpdatedAt()
        );
    }

    public StockResponse toStockResponse(Stock s) {
        return new StockResponse(
                s.getSymbol(),
                s.getName(),
                s.getExchange(),
                s.getCurrentPrice(),
                s.getOpenPrice(),
                s.getDayHigh(),
                s.getDayLow(),
                s.getVolume(),
                s.getPriceChangePercent(),
                s.getPriceChangeAmount(),
                s.getLastUpdated()
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

    public CandleResponse toCandleResponse(StockCandle c) {
        return new CandleResponse(
                c.getCandleDate(),
                c.getOpen(),
                c.getHigh(),
                c.getLow(),
                c.getClose(),
                c.getVolume()
        );
    }

    public CandleResponse toCandleResponse(ForexCandle c) {
        return new CandleResponse(
                c.getCandleDate(),
                c.getOpen(),
                c.getHigh(),
                c.getLow(),
                c.getClose(),
                null
        );
    }

    public List<CandleResponse> toCryptoCandleResponses(List<CryptoCandle> candles) {
        return candles.stream().map(this::toCandleResponse).toList();
    }

    public List<CandleResponse> toStockCandleResponses(List<StockCandle> candles) {
        return candles.stream().map(this::toCandleResponse).toList();
    }

    public List<CandleResponse> toForexCandleResponses(List<ForexCandle> candles) {
        return candles.stream().map(this::toCandleResponse).toList();
    }
}
