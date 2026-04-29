package com.finance.backend.service;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.mapper.CommodityMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.CommoditySnapshotInput;
import com.finance.backend.repository.CommodityCandleRepository;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.CandleBatchUpsertTemplate;
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

    public void applySnapshot(Commodity commodity, CommoditySnapshotInput snapshot,
                              String yahooSymbol, int scale) {
        commodity.applyPriceSnapshot(snapshot, scale);
        if (commodity.getYahooSymbol() == null) {
            commodity.setYahooSymbol(yahooSymbol);
        }
        commodityRepository.save(commodity);
    }

    public void upsertCandles(Commodity commodity, List<YahooCandleDto> candleDtos, int scale) {
        if (commodity.getCommodityCode() == null || commodity.getCommodityCode().isBlank()) return;
        if (commodityRepository.findById(commodity.getCommodityCode()).isEmpty()) {
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
