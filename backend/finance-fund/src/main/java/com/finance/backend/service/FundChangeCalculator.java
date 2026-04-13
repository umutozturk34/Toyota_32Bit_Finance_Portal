package com.finance.backend.service;

import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class FundChangeCalculator {

    private static final int SCALE = 4;

    private final FundCandleRepository fundCandleRepository;

    public BigDecimal calculateChangePercent(String fundCode, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        List<FundCandle> recent = fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc(fundCode);
        if (recent.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal previousPrice = recent.get(1).getPrice();
        if (previousPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return currentPrice.subtract(previousPrice)
                .multiply(new BigDecimal("100"))
                .divide(previousPrice, SCALE, RoundingMode.HALF_UP);
    }
}
