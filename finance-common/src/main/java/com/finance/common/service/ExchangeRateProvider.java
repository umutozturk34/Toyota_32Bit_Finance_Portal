package com.finance.common.service;

import com.finance.common.dto.external.YahooCandleDto;

import java.util.Map;

public interface ExchangeRateProvider {

    ExchangeRateSnapshot getCurrentUsdTry();

    Map<String, YahooCandleDto> getUsdTryHistory();
}
