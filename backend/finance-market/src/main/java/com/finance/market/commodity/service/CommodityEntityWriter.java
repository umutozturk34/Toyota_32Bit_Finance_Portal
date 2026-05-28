package com.finance.market.commodity.service;
import com.finance.market.core.service.MarketEntityWriter;


import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.commodity.mapper.CommodityMapper;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.market.commodity.model.CommoditySnapshotInput;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import com.finance.market.commodity.repository.CommodityRepository;
import com.finance.market.core.util.CandleBatchUpsertTemplate;
import com.finance.market.core.util.ChangeFromCandlesUpdater;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persists commodity snapshots and TRY candles. Candles are de-duplicated and keyed by day (dates
 * normalized to start-of-day) before an idempotent batch upsert; change percent is back-filled from
 * candles only when missing.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class CommodityEntityWriter implements MarketEntityWriter {

    private final CommodityRepository commodityRepository;
    private final CommodityCandleRepository commodityCandleRepository;
    private final CommodityMapper commodityMapper;
    private final AssetRegistryService assetRegistry;

    public void applySnapshot(Commodity commodity, CommoditySnapshotInput snapshot,
                              String yahooSymbol, int scale) {
        commodity.applyPriceSnapshot(snapshot, scale);
        if (commodity.getYahooSymbol() == null) {
            commodity.setYahooSymbol(yahooSymbol);
        }
        commodity.setAsset(assetRegistry.upsert(MarketType.COMMODITY, commodity.getCommodityCode()));
        commodityRepository.save(commodity);
    }

    public boolean refreshChangePercentFromCandles(Commodity commodity, int scale) {
        List<CommodityCandle> top2 = commodityCandleRepository
                .findTop2ByCommodityCodeOrderByCandleDateDesc(commodity.getCommodityCode());
        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                commodity, commodity.getCurrentPrice(), top2, scale);
        if (changed) commodityRepository.save(commodity);
        return changed;
    }

    public void upsertCandles(Commodity commodity, List<YahooCandleDto> candleDtos, int scale) {
        if (commodity.getCommodityCode() == null || commodity.getCommodityCode().isBlank()) return;
        if (commodityRepository.findById(commodity.getCommodityCode()).isEmpty()) {
            commodity.setAsset(assetRegistry.upsert(MarketType.COMMODITY, commodity.getCommodityCode()));
            commodityRepository.save(commodity);
        }
        List<YahooCandleDto> uniqueDtos = candleDtos.stream()
                .collect(Collectors.toMap(
                        dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                        dto -> dto, (a, b) -> b, LinkedHashMap::new))
                .values().stream().toList();
        CandleBatchUpsertTemplate.UpsertResult<CommodityCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                uniqueDtos,
                dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                keys -> commodityCandleRepository.findByCommodityCodeAndCandleDateIn(commodity.getCommodityCode(), keys),
                candle -> candle.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                (existing, dto) -> {
                    commodityMapper.updateCandleEntity(existing, dto);
                    normalize(existing, scale);
                },
                dto -> {
                    CommodityCandle candle = commodityMapper.toCandleEntity(dto, commodity.getCommodityCode(), commodity);
                    normalize(candle, scale);
                    return candle;
                });
        if (!upsertResult.newEntities().isEmpty()) {
            commodityCandleRepository.saveAll(upsertResult.newEntities());
        }
    }

    /** Scales/normalizes OHLC and collapses the candle date to start-of-day for one-per-day storage. */
    private void normalize(CommodityCandle candle, int scale) {
        candle.scaleAndNormalizeOhlc(scale);
        LocalDateTime candleDate = candle.getCandleDate();
        if (candleDate != null) {
            candle.setCandleDate(candleDate.toLocalDate().atStartOfDay());
        }
    }
}
