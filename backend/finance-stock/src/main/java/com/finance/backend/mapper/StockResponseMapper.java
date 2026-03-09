package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.StockResponse;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockResponseMapper {

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

    public List<CandleResponse> toStockCandleResponses(List<StockCandle> candles) {
        return candles.stream().map(this::toCandleResponse).toList();
    }
}
