package com.finance.market.forex.service;
import com.finance.market.core.service.MarketRefresher;


import com.finance.market.forex.config.ForexProperties;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.forex.mapper.ForexMapper;
import com.finance.market.forex.model.Forex;
import com.finance.common.model.MarketType;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.common.util.BatchLogHelper;
import com.finance.common.util.BatchUpdateRunner;
import com.finance.market.core.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class ForexUpdateService implements MarketRefresher {

    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final ForexSnapshotProcessor snapshotProcessor;
    private final ForexMapper forexMapper;
    private final String baseCurrency;
    private final int batchMinSample;

    public ForexUpdateService(ForexRepository forexRepository,
                              ForexCandleRepository forexCandleRepository,
                              ForexSnapshotProcessor snapshotProcessor,
                              ForexMapper forexMapper,
                              ForexProperties forexProperties) {
        this.forexRepository = forexRepository;
        this.forexCandleRepository = forexCandleRepository;
        this.snapshotProcessor = snapshotProcessor;
        this.forexMapper = forexMapper;
        this.baseCurrency = forexProperties.getBaseCurrency();
        this.batchMinSample = forexProperties.getBatchMinSample();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FOREX;
    }

    @Override
    public void refreshAll() {
        List<Forex> allForex = forexRepository.findAll();
        log.info("Starting Yahoo forex sync for {} pairs", allForex.size());

        Forex usdtry = allForex.stream()
                .filter(f -> baseCurrency.equals(f.getCurrencyCode()))
                .findFirst()
                .orElse(null);
        if (usdtry == null) {
            log.error("{} not found, skipping forex sync", baseCurrency);
            return;
        }
        snapshotProcessor.updatePair(usdtry, Map.of());
        Map<String, YahooCandleDto> usdtryCandleMap = buildUsdtryCandleMap();

        List<Forex> nonUsdTryForex = allForex.stream()
                .filter(forex -> !baseCurrency.equals(forex.getCurrencyCode()))
                .toList();

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                nonUsdTryForex,
                forex -> {
                    snapshotProcessor.updatePair(forex, usdtryCandleMap);
                },
                Forex::getCurrencyCode,
                log, "Forex", "update", batchMinSample);
        BatchLogHelper.logSummary(log, "Yahoo forex sync", result);
    }

    @Override
    public void refresh(String code) {
        snapshotProcessor.refreshOne(code);
    }

    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

    private Map<String, YahooCandleDto> buildUsdtryCandleMap() {
        return forexCandleRepository.findByCurrencyCodeOrderByCandleDateAsc(baseCurrency).stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        forexMapper::toYahooCandleDto,
                        (a, b) -> a));
    }

}
