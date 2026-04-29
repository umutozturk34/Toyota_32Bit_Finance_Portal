package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.util.PercentChangeCalculator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

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

    public BigDecimal calculateChangePercent(String fundCode, BigDecimal currentPrice, LocalDateTime currentDate) {
        if (currentPrice == null || currentPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        Optional<FundCandle> previous = fundCandleRepository
                .findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(fundCode, currentDate);
        if (previous.isEmpty()) {
            return BigDecimal.ZERO;
        }
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                currentPrice, previous.get().getPrice(), scale);
        return result.percent() != null ? result.percent() : BigDecimal.ZERO;
    }
}
