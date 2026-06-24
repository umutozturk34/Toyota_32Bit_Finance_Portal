package com.finance.market.fund.service;
import com.finance.market.core.service.TrackedAssetCommandService;

import com.finance.market.core.service.MarketEntityWriter;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.common.config.AppProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundDto;
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

/**
 * Persists fund snapshots and candles, auto-tracks newly seen funds, and recomputes change percent
 * against the prior candle. Candle batches drop rows with no/zero price before an idempotent upsert.
 */
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

    /** Upserts the fund by code (update in place when it exists) and links its asset-registry entry. */
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

    /**
     * Recomputes change vs. the candle before {@code currentDate}; saves and returns true only if it changed.
     * Resolves the previous close with its own query — for single-fund callers (incremental refresh). A batch
     * caller should pre-load all prior closes and use {@link #refreshChangePercent(Fund, LocalDateTime, BigDecimal)}.
     */
    public boolean refreshChangePercent(Fund fund, LocalDateTime currentDate) {
        if (fund.getPrice() == null || currentDate == null) return false;
        BigDecimal previousPrice = fundCandleRepository
                .findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(fund.getFundCode(), currentDate)
                .map(FundCandle::getPrice)
                .orElse(null);
        return refreshChangePercent(fund, currentDate, previousPrice);
    }

    /**
     * Batch variant: the caller supplies the already-resolved previous close (e.g. from a single grouped
     * query over all funds), so a whole-universe recompute does not issue a prior-candle query per fund.
     */
    public boolean refreshChangePercent(Fund fund, LocalDateTime currentDate, BigDecimal previousPrice) {
        if (fund.getPrice() == null || currentDate == null) return false;
        BigDecimal oldPercent = fund.getChangePercent();
        fund.applyChange(fund.getPrice(), previousPrice, scale);
        if (Objects.equals(oldPercent, fund.getChangePercent())) return false;
        fundRepository.save(fund);
        return true;
    }

    /**
     * Idempotently upserts a candle batch, dropping rows with no/zero price before the upsert.
     *
     * @return the number of candles inserted or updated
     */
    public int saveCandleBatch(Fund fund, FundType fundType, List<TefasFundDto> dtos) {
        List<TefasFundDto> priced = dtos.stream().filter(FundEntityWriter::hasValidPrice).toList();
        CandleBatchUpsertTemplate.UpsertResult<FundCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                priced,
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

    /** Upserts a single candle for one date; a no/zero-price row is ignored. */
    public void upsertCandleFromDto(Fund fund, FundType fundType, TefasFundDto dto) {
        if (!hasValidPrice(dto)) return;
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

    private static boolean hasValidPrice(TefasFundDto dto) {
        return dto != null
                && dto.price() != null
                && dto.price().signum() != 0;
    }

    /** Auto-tracks a BYF (exchange-traded) fund the first time it is seen; a no-op if already tracked. */
    public void ensureByfTracked(String fundCode, String tefasName) {
        trackedAssetCommandService.autoTrack(TrackedAssetType.FUND, fundCode, tefasName, autoTrackSortOrder);
    }

    /** Auto-tracks a YAT (mutual) fund the first time it is seen; a no-op if already tracked. */
    public void ensureYatTracked(String fundCode, String tefasName) {
        trackedAssetCommandService.autoTrack(TrackedAssetType.FUND, fundCode, tefasName, autoTrackSortOrder);
    }
}
