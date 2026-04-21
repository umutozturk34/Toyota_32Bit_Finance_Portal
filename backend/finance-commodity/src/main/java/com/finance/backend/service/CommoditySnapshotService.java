package com.finance.backend.service;

import com.finance.backend.client.YahooCommodityClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.SyntheticPriceCalculator;
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
    private final CommodityRepository commodityRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final TransactionTemplate transactionTemplate;
    private final int scale;
    private final BigDecimal spreadRate;

    public CommoditySnapshotService(YahooCommodityClient yahooCommodityClient,
                                    CommodityRepository commodityRepository,
                                    MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                    MarketCacheService<Forex, ForexCandle> forexCacheService,
                                    PlatformTransactionManager transactionManager,
                                    AppProperties appProperties) {
        this.yahooCommodityClient = yahooCommodityClient;
        this.commodityRepository = commodityRepository;
        this.commodityCacheService = commodityCacheService;
        this.forexCacheService = forexCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.scale = appProperties.getScale();
        this.spreadRate = appProperties.getCommodity().getSpreadRate();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.COMMODITY;
    }

    @Override
    public void refreshAll() {
        List<Commodity> yahooTracked = commodityRepository.findAllByOrderByCommodityCodeAsc().stream()
                .filter(c -> c.getYahooSymbol() != null && !c.getYahooSymbol().isBlank())
                .toList();

        log.info("Starting commodity snapshot sync for {} items", yahooTracked.size());

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                yahooTracked,
                this::updateCommoditySnapshot,
                Commodity::getCommodityCode,
                "snapshot",
                5,
                (commodity, e) -> log.error("Snapshot failed for {}: {}", commodity.getCommodityCode(), e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Yahoo CB is OPEN, stopping commodity sync. {} success, {} failed so far",
                        stopped.successCount(), stopped.failCount()));

        BatchLogHelper.logSummary(log, "Commodity snapshot sync", result);
    }

    private void updateCommoditySnapshot(Commodity commodity) {
        YahooQuoteDto quote = yahooCommodityClient.fetchQuote(commodity.getYahooSymbol());
        if (quote == null || quote.regularMarketPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    "No price for " + commodity.getCommodityCode());
        }
        BigDecimal usdtryRate = getUsdTryRate();
        BigDecimal tryPrice = SyntheticPriceCalculator.calculateSyntheticPrice(
                quote.regularMarketPrice(), usdtryRate, false, scale);
        BigDecimal tryPreviousClose = SyntheticPriceCalculator.calculateSyntheticPrice(
                quote.previousClose(), usdtryRate, false, scale);
        transactionTemplate.executeWithoutResult(status -> {
            commodity.applyYahooSnapshot(tryPrice, tryPreviousClose, spreadRate, scale);
            commodityRepository.save(commodity);
        });
        commodityCacheService.putSnapshot(commodity.getCommodityCode(), commodity);
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
