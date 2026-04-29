package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.util.PercentChangeCalculator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Log4j2
@Component
public class FundChangeCalculator {

    private final FundCandleRepository fundCandleRepository;
    private final int scale;

    public FundChangeCalculator(FundCandleRepository fundCandleRepository,
                                AppProperties appProperties) {
        this.fundCandleRepository = fundCandleRepository;
        this.scale = appProperties.getScale();
    }

    public BigDecimal calculateChangePercent(String fundCode, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }

        List<FundCandle> recent = fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc(fundCode);
        if (recent.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal previousPrice = recent.get(1).getPrice();
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(currentPrice, previousPrice, scale);
        return result.percent() != null ? result.percent() : BigDecimal.ZERO;
    }
}
