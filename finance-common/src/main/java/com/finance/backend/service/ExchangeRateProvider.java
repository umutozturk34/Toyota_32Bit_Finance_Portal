package com.finance.backend.service;

import com.finance.backend.dto.external.YahooCandleDto;

import java.util.Map;

public interface ExchangeRateProvider {

    ExchangeRateSnapshot getCurrentUsdTry();

    Map<String, YahooCandleDto> getUsdTryHistory();
}
