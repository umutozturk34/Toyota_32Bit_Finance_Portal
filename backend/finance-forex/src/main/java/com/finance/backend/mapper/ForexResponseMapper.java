package com.finance.backend.mapper;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.ForexResponse;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ForexResponseMapper {

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

    public List<CandleResponse> toForexCandleResponses(List<ForexCandle> candles) {
        return candles.stream().map(this::toCandleResponse).toList();
    }
}
