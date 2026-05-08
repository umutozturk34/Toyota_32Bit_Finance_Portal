package com.finance.market.core.service;

import com.finance.market.core.dto.external.YahooCandleDto;

import java.util.Map;

public interface ExchangeRateProvider {

    ExchangeRateSnapshot getCurrentUsdTry();

    Map<String, YahooCandleDto> getUsdTryHistory();
}
