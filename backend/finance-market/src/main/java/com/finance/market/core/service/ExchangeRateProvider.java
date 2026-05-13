package com.finance.market.core.service;

import java.math.BigDecimal;
import java.util.Map;

public interface ExchangeRateProvider {

    ExchangeRateSnapshot getCurrentUsdTry();

    Map<String, BigDecimal> getUsdTryHistory();
}
