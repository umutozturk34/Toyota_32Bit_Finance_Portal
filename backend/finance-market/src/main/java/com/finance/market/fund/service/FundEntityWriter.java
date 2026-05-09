package com.finance.market.fund.service;
import com.finance.market.core.service.TrackedAssetCommandService;

import com.finance.market.core.service.MarketEntityWriter;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.common.config.AppProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.fund.mapper.FundMapper;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import com.finance.market.fund.model.FundType;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import com.finance.market.core.util.CandleBatchUpsertTemplate;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Log4j2
@Component
public class FundEntityWriter implements MarketEntityWriter {

    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final FundMapper fundMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final AssetRegistryService assetRegistry;
    private final int autoTrackSortOrder;
    private final int scale;

    public FundEntityWriter(FundRepository fundRepository,
                            FundCandleRepository fundCandleRepository,
                            FundMapper fundMapper,
                            TrackedAssetQueryService trackedAssetQueryService,
                            TrackedAssetCommandService trackedAssetCommandService,
                            AssetRegistryService assetRegistry,
                            AppProperties appProperties,
                            FundProperties fundProperties) {
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.fundMapper = fundMapper;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.trackedAssetCommandService = trackedAssetCommandService;
        this.assetRegistry = assetRegistry;
        this.autoTrackSortOrder = fundProperties.getAutoTrackSortOrder();
        this.scale = appProperties.getScale();
    }

    public Fund saveSnapshot(TefasFundDto dto, FundType fundType) {
        LocalDateTime now = LocalDateTime.now();
        Fund existing = fundRepository.findById(dto.fundCode()).orElse(null);
        Fund toPersist;
        if (existing != null) {
            fundMapper.updateEntity(existing, dto, fundType, now);
            toPersist = existing;
        } else {
            toPersist = fundMapper.toEntity(dto, fundType, now);
        }
        toPersist.setAsset(assetRegistry.upsert(MarketType.FUND, dto.fundCode()));
        fundRepository.save(toPersist);
        log.debug("Saved snapshot: {} ({}) - {}", dto.fundCode(), fundType, dto.price());
        return toPersist;
    }

    public boolean refreshChangePercent(Fund fund, LocalDateTime currentDate) {
        if (fund.getPrice() == null || currentDate == null) return false;
        BigDecimal previousPrice = fundCandleRepository
                .findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(fund.getFundCode(), currentDate)
                .map(FundCandle::getPrice)
                .orElse(null);
        BigDecimal oldPercent = fund.getChangePercent();
        fund.applyChange(fund.getPrice(), previousPrice, scale);
        if (Objects.equals(oldPercent, fund.getChangePercent())) return false;
        fundRepository.save(fund);
        return true;
    }

    public int saveCandleBatch(Fund fund, FundType fundType, List<TefasFundDto> dtos) {
        CandleBatchUpsertTemplate.UpsertResult<FundCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                dtos,
                TefasFundDto::date,
                keys -> fundCandleRepository.findByFundCodeAndCandleDateIn(fund.getFundCode(), keys),
                FundCandle::getCandleDate,
                fundMapper::updateCandleEntity,
                dto -> fundMapper.toCandleEntity(dto, fund, fundType));
        if (!upsertResult.newEntities().isEmpty()) {
            fundCandleRepository.saveAll(upsertResult.newEntities());
        }
        return upsertResult.totalChanged();
    }

    public void upsertCandleFromDto(Fund fund, FundType fundType, TefasFundDto dto) {
        FundCandle existing = fundCandleRepository
                .findByFundCodeAndCandleDate(fund.getFundCode(), dto.date())
                .orElse(null);
        if (existing != null) {
            fundMapper.updateCandleEntity(existing, dto);
            fundCandleRepository.save(existing);
        } else {
            FundCandle candle = fundMapper.toCandleEntity(dto, fund, fundType);
            fundCandleRepository.save(candle);
        }
    }

    public void ensureByfTracked(String fundCode, String tefasName) {
        trackedAssetCommandService.autoTrack(TrackedAssetType.FUND, fundCode, tefasName, autoTrackSortOrder);
    }
}
