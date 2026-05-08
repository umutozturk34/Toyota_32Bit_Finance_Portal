package com.finance.commodity.service;
import com.finance.common.service.MarketEntityWriter;


import com.finance.common.dto.external.YahooCandleDto;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetRegistryService;
import com.finance.commodity.mapper.CommodityMapper;
import com.finance.commodity.model.Commodity;
import com.finance.commodity.model.CommodityCandle;
import com.finance.commodity.model.CommoditySnapshotInput;
import com.finance.commodity.repository.CommodityCandleRepository;
import com.finance.commodity.repository.CommodityRepository;
import com.finance.common.util.CandleBatchUpsertTemplate;
import com.finance.common.util.ChangeFromCandlesUpdater;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

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
        commodity.setAsset(assetRegistry.upsert(MarketType.COMMODITY, commodity.getCommodityCode(), commodity.resolveDisplayName()));
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
            commodity.setAsset(assetRegistry.upsert(MarketType.COMMODITY, commodity.getCommodityCode(), commodity.resolveDisplayName()));
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

    private void normalize(CommodityCandle candle, int scale) {
        candle.scaleAndNormalizeOhlc(scale);
        LocalDateTime candleDate = candle.getCandleDate();
        if (candleDate != null) {
            candle.setCandleDate(candleDate.toLocalDate().atStartOfDay());
        }
    }
}
