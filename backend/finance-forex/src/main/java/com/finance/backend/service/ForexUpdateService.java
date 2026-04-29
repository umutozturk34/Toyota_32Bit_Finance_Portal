package com.finance.backend.service;

import com.finance.backend.config.ForexProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class ForexUpdateService implements SnapshotBatchRefresher, CandleBatchRefresher {

    private static final String USDTRY = "USDTRY";
    private static final int BATCH_PARALLELISM = 5;

    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final ForexSnapshotProcessor snapshotProcessor;
    private final ForexMapper forexMapper;
    private final TransactionTemplate transactionTemplate;
    private final int yearsToKeep;

    public ForexUpdateService(ForexRepository forexRepository,
                              ForexCandleRepository forexCandleRepository,
                              MarketCacheService<Forex, ForexCandle> forexCacheService,
                              ForexSnapshotProcessor snapshotProcessor,
                              ForexMapper forexMapper,
                              TransactionTemplate transactionTemplate,
                              ForexProperties forexProperties) {
        this.forexRepository = forexRepository;
        this.forexCandleRepository = forexCandleRepository;
        this.forexCacheService = forexCacheService;
        this.snapshotProcessor = snapshotProcessor;
        this.forexMapper = forexMapper;
        this.transactionTemplate = transactionTemplate;
        this.yearsToKeep = forexProperties.getYearsToKeep();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FOREX;
    }

    @Override
    public void refreshAll() {
        pruneOldForexCandles();
        List<Forex> allForex = forexRepository.findAll();
        log.info("Starting Yahoo forex sync for {} pairs", allForex.size());

        Forex usdtry = allForex.stream()
                .filter(f -> USDTRY.equals(f.getCurrencyCode()))
                .findFirst()
                .orElse(null);
        if (usdtry == null) {
            log.error("USDTRY not found, skipping forex sync");
            return;
        }
        snapshotProcessor.updatePair(usdtry, Map.of());
        forexCacheService.refreshHistory(USDTRY);
        Map<String, YahooCandleDto> usdtryCandleMap = buildUsdtryCandleMap();

        List<Forex> nonUsdTryForex = allForex.stream()
                .filter(forex -> !USDTRY.equals(forex.getCurrencyCode()))
                .toList();

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                nonUsdTryForex,
                forex -> {
                    snapshotProcessor.updatePair(forex, usdtryCandleMap);
                    forexCacheService.refreshHistory(forex.getCurrencyCode());
                },
                Forex::getCurrencyCode,
                log, "Forex", "update", BATCH_PARALLELISM);
        BatchLogHelper.logSummary(log, "Yahoo forex sync", result);
    }

    @Override
    public void refreshSnapshot(String code) {
        snapshotProcessor.refreshOne(code);
    }

    @Override
    public void refreshCandles(String code) {
        snapshotProcessor.refreshOne(code);
    }

    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

    private Map<String, YahooCandleDto> buildUsdtryCandleMap() {
        return forexCacheService.getHistory(USDTRY).stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        forexMapper::toYahooCandleDto,
                        (a, b) -> a));
    }

    private void pruneOldForexCandles() {
        CandlePruner.pruneByYears(transactionTemplate, yearsToKeep,
                cutoffDate -> forexCandleRepository.deleteByCandleDateBefore(cutoffDate));
    }
}
