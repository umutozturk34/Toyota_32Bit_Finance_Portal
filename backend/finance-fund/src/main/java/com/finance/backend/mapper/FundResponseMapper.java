package com.finance.backend.mapper;

import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.dto.response.FundResponse;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FundResponseMapper {

    public FundResponse toFundResponse(Fund f) {
        return new FundResponse(
                f.getFundCode(),
                f.getName(),
                f.getFundType(),
                f.getPrice(),
                f.getBulletinPrice(),
                f.getShareCount(),
                f.getInvestorCount(),
                f.getPortfolioSize(),
                f.getLastUpdated()
        );
    }

    public List<FundResponse> toFundResponses(List<Fund> funds) {
        return funds.stream().map(this::toFundResponse).toList();
    }

    public FundCandleResponse toFundCandleResponse(FundCandle c) {
        return new FundCandleResponse(
                c.getCandleDate(),
                c.getFundType(),
                c.getPrice(),
                c.getBulletinPrice(),
                c.getShareCount(),
                c.getInvestorCount(),
                c.getPortfolioSize()
        );
    }

    public List<FundCandleResponse> toFundCandleResponses(List<FundCandle> candles) {
        return candles.stream().map(this::toFundCandleResponse).toList();
    }
}
