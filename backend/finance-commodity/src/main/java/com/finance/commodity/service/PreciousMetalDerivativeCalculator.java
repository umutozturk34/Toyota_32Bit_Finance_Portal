package com.finance.commodity.service;
import com.finance.cache.service.MarketCacheService;


import com.finance.common.config.AppProperties;
import com.finance.commodity.config.CommodityProperties;
import com.finance.commodity.config.CommodityProperties.DerivativeRule;
import com.finance.commodity.model.Commodity;
import com.finance.commodity.model.CommodityCandle;
import com.finance.commodity.model.CommoditySnapshotInput;
import com.finance.commodity.repository.CommodityCandleRepository;
import com.finance.commodity.repository.CommodityRepository;
import com.finance.common.util.SyntheticPriceCalculator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Service
@Log4j2
public class PreciousMetalDerivativeCalculator {

    private final CommodityRepository commodityRepository;
    private final CommodityCandleRepository commodityCandleRepository;
    private final MarketCacheService<Commodity> commodityCacheService;
    private final CommoditySegmentResolver segmentResolver;
    private final int scale;
    private final List<DerivativeRule> rules;

    public PreciousMetalDerivativeCalculator(CommodityRepository commodityRepository,
                                             CommodityCandleRepository commodityCandleRepository,
                                             MarketCacheService<Commodity> commodityCacheService,
                                             CommoditySegmentResolver segmentResolver,
                                             AppProperties appProperties,
                                             CommodityProperties commodityProperties) {
        this.commodityRepository = commodityRepository;
        this.commodityCandleRepository = commodityCandleRepository;
        this.commodityCacheService = commodityCacheService;
        this.segmentResolver = segmentResolver;
        this.scale = appProperties.getScale();
        this.rules = List.copyOf(commodityProperties.getDerivatives());
    }

    public boolean hasDerivatives(String commodityCode) {
        return rules.stream().anyMatch(r -> r.getSourceCode().equals(commodityCode));
    }

    public boolean isKnownDerivative(String commodityCode) {
        return rules.stream().anyMatch(r -> r.getDerivativeCode().equals(commodityCode));
    }

    public void refreshDerivatives(Commodity source, BigDecimal usdTryCurrent, BigDecimal usdTryPrevious) {
        if (source == null || source.getCurrentPriceUsd() == null || usdTryCurrent == null) {
            log.debug("Skipping derivatives: missing source USD price or USDTRY rate for {}",
                    source == null ? "null" : source.getCommodityCode());
            return;
        }
        BigDecimal usdTryForPrevious = usdTryPrevious != null ? usdTryPrevious : usdTryCurrent;
        rules.stream()
                .filter(rule -> rule.getSourceCode().equals(source.getCommodityCode()))
                .forEach(rule -> applyRule(rule, source, usdTryForPrevious));
    }

    public void refreshDerivativeCandles() {
        rules.forEach(this::refreshCandlesFromRule);
    }

    private void applyRule(DerivativeRule rule, Commodity source, BigDecimal usdTryForPrevious) {
        BigDecimal current = divide(source.getCurrentPrice(), rule.getDivisor());
        BigDecimal open = divide(source.getOpenPrice(), rule.getDivisor());
        BigDecimal high = divide(source.getDayHigh(), rule.getDivisor());
        BigDecimal low = divide(source.getDayLow(), rule.getDivisor());
        BigDecimal previous = previousFromOnsUsd(source.getPreviousPriceUsd(), usdTryForPrevious, rule.getDivisor());
        CommoditySnapshotInput snapshot = new CommoditySnapshotInput(
                current, previous, null, null, open, high, low, source.getVolume());
        persistDerivative(rule.getDerivativeCode(), snapshot);
    }

    private void refreshCandlesFromRule(DerivativeRule rule) {
        List<CommodityCandle> sourceCandles = commodityCandleRepository
                .findByCommodityCodeOrderByCandleDateAsc(rule.getSourceCode());
        if (sourceCandles.isEmpty()) {
            log.debug("No source candles for {} — skipping derivative candle refresh", rule.getSourceCode());
            return;
        }
        UnaryOperator<BigDecimal> transform = value -> divide(value, rule.getDivisor());
        regenerateDerivativeCandles(rule.getDerivativeCode(), sourceCandles, transform);
    }

    private void regenerateDerivativeCandles(String derivativeCode, List<CommodityCandle> sourceCandles,
                                              UnaryOperator<BigDecimal> transform) {
        Commodity derivative = commodityRepository.findById(derivativeCode).orElse(null);
        if (derivative == null) {
            log.debug("Derivative commodity {} not found — run snapshot first", derivativeCode);
            return;
        }

        Map<LocalDateTime, CommodityCandle> existingByDate = commodityCandleRepository
                .findByCommodityCodeOrderByCandleDateAsc(derivativeCode).stream()
                .collect(Collectors.toMap(CommodityCandle::getCandleDate, Function.identity(), (a, b) -> {
                    log.warn("Duplicate derivative candle for {} at {}, keeping first", derivativeCode, a.getCandleDate());
                    return a;
                }));

        List<CommodityCandle> toInsert = new ArrayList<>();
        List<CommodityCandle> toUpdate = new ArrayList<>();
        for (CommodityCandle src : sourceCandles) {
            CommodityCandle existing = existingByDate.get(src.getCandleDate());
            if (existing != null) {
                applyTransform(existing, src, transform);
                toUpdate.add(existing);
            } else {
                CommodityCandle fresh = CommodityCandle.builder()
                        .commodity(derivative)
                        .commodityCode(derivativeCode)
                        .candleDate(src.getCandleDate())
                        .open(transform.apply(src.getOpen()))
                        .high(transform.apply(src.getHigh()))
                        .low(transform.apply(src.getLow()))
                        .close(transform.apply(src.getClose()))
                        .build();
                fresh.scaleAndNormalizeOhlc(scale);
                toInsert.add(fresh);
            }
        }
        if (!toInsert.isEmpty()) {
            commodityCandleRepository.saveAll(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            commodityCandleRepository.saveAll(toUpdate);
        }
        log.info("Derivative candles refreshed for {}: {} inserted, {} updated", derivativeCode, toInsert.size(), toUpdate.size());
    }

    private void applyTransform(CommodityCandle target, CommodityCandle source, UnaryOperator<BigDecimal> transform) {
        target.setOpen(transform.apply(source.getOpen()));
        target.setHigh(transform.apply(source.getHigh()));
        target.setLow(transform.apply(source.getLow()));
        target.setClose(transform.apply(source.getClose()));
        target.scaleAndNormalizeOhlc(scale);
    }

    private BigDecimal previousFromOnsUsd(BigDecimal onsUsd, BigDecimal usdTryRate, BigDecimal divisor) {
        if (onsUsd == null) return null;
        BigDecimal onsTry = onsUsd.multiply(usdTryRate);
        return divide(onsTry, divisor);
    }

    private void persistDerivative(String code, CommoditySnapshotInput snapshot) {
        if (snapshot.tryPrice() == null) return;
        Commodity derivative = commodityRepository.findById(code)
                .orElseGet(() -> Commodity.builder()
                        .commodityCode(code)
                        .commoditySegment(segmentResolver.resolve(code))
                        .build());
        derivative.applyPriceSnapshot(snapshot, scale);
        commodityRepository.save(derivative);
        commodityCacheService.putSnapshot(code, derivative);
    }

    private BigDecimal divide(BigDecimal value, BigDecimal divisor) {
        return SyntheticPriceCalculator.safeDivide(value, divisor, scale);
    }
}
