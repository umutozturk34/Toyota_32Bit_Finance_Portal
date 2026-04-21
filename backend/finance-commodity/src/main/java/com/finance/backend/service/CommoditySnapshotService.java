package com.finance.backend.service;

import com.finance.backend.client.YahooCommodityClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.CommodityMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.CommoditySnapshotInput;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
@Log4j2
public class CommoditySnapshotService implements SnapshotBatchRefresher {

    private final YahooCommodityClient yahooCommodityClient;
    private final CommodityMapper commodityMapper;
    private final CommodityRepository commodityRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;
    private final int scale;
    private final BigDecimal spreadRate;

    public CommoditySnapshotService(YahooCommodityClient yahooCommodityClient,
                                    CommodityMapper commodityMapper,
                                    CommodityRepository commodityRepository,
                                    MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                    MarketCacheService<Forex, ForexCandle> forexCacheService,
                                    PreciousMetalDerivativeCalculator derivativeCalculator,
                                    TrackedAssetQueryService trackedAssetQueryService,
                                    PlatformTransactionManager transactionManager,
                                    AppProperties appProperties) {
        this.yahooCommodityClient = yahooCommodityClient;
        this.commodityMapper = commodityMapper;
        this.commodityRepository = commodityRepository;
        this.commodityCacheService = commodityCacheService;
        this.forexCacheService = forexCacheService;
        this.derivativeCalculator = derivativeCalculator;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.scale = appProperties.getScale();
        this.spreadRate = appProperties.getCommodity().getSpreadRate();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.COMMODITY;
    }

    public boolean existsInApi(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank() || !isYahooFetchable(normalized)) return false;
        try {
            YahooQuoteDto quote = yahooCommodityClient.fetchQuote(normalized);
            return quote != null && quote.regularMarketPrice() != null;
        } catch (Exception e) {
            log.warn("Commodity existence check failed for {}: {}", normalized, e.getMessage());
            return false;
        }
    }

    public void refreshTrackedCommoditySnapshot(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank() || !isYahooFetchable(normalized)) return;
        updateCommoditySnapshot(normalized);
        log.info("Refreshed tracked commodity snapshot for {}", normalized);
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    @Override
    public void refreshAll() {
        List<String> enabledCodes = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.COMMODITY);
        List<String> yahooCodes = enabledCodes.stream()
                .filter(this::isYahooFetchable)
                .toList();
        if (yahooCodes.isEmpty()) {
            log.info("No Yahoo-fetchable commodities enabled, skipping snapshot sync");
            return;
        }

        log.info("Starting commodity snapshot sync for {} items", yahooCodes.size());

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                yahooCodes,
                this::updateCommoditySnapshot,
                code -> code,
                "snapshot",
                5,
                (code, e) -> log.error("Snapshot failed for {}: {}", code, e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Yahoo CB is OPEN, stopping commodity sync. {} success, {} failed so far",
                        stopped.successCount(), stopped.failCount()));

        BatchLogHelper.logSummary(log, "Commodity snapshot sync", result);
    }

    private boolean isYahooFetchable(String code) {
        return code != null && code.contains("=F");
    }

    private void updateCommoditySnapshot(String yahooSymbol) {
        YahooQuoteDto quote = yahooCommodityClient.fetchQuote(yahooSymbol);
        if (quote == null || quote.regularMarketPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    "No price for " + yahooSymbol);
        }
        BigDecimal usdtryRate = getUsdTryRate();
        CommoditySnapshotInput snapshot = commodityMapper.toSnapshotInput(quote, usdtryRate, scale);
        Commodity commodity = commodityRepository.findById(yahooSymbol)
                .orElseGet(() -> Commodity.builder().commodityCode(yahooSymbol).build());
        transactionTemplate.executeWithoutResult(status -> {
            commodity.applyYahooSnapshot(snapshot, spreadRate, scale);
            commodityRepository.save(commodity);
        });
        commodityCacheService.putSnapshot(yahooSymbol, commodity);
        if (derivativeCalculator.hasDerivatives(yahooSymbol)) {
            derivativeCalculator.refreshDerivatives(commodity, usdtryRate);
        }
    }

    private BigDecimal getUsdTryRate() {
        Forex usdtry = forexCacheService.getSnapshot("USDTRY");
        if (usdtry == null || usdtry.getCurrentPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY rate not available for commodity TRY conversion");
        }
        return usdtry.getCurrentPrice();
    }
}
