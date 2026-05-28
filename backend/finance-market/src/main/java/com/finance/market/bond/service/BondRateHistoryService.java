package com.finance.market.bond.service;
import com.finance.market.core.cache.MarketCacheService;



import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.bond.mapper.BondMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.bond.util.BondSerieFilterUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists a batch of bond snapshots: upserts each bond, fetches missing rate history (from the last
 * stored date or maturity start), adds today's rate from the snapshot, then classifies the bond type
 * and computes its yield. Per-bond failures are logged and skipped. Saved bonds are cached.
 */
@Service
@Log4j2
public class BondRateHistoryService {

    private final BondMapper bondMapper;
    private final BondRepository bondRepository;
    private final BondRateHistoryRepository rateHistoryRepository;
    private final MarketCacheService<Bond> bondCacheService;
    private final TransactionTemplate transactionTemplate;
    private final BondRateFetcher rateFetcher;
    private final AssetRegistryService assetRegistry;
    private final BigDecimal auctionThreshold;
    private final BigDecimal cpiFixedThreshold;
    private final BigDecimal faceValue;
    private final int daysInYear;

    public BondRateHistoryService(BondMapper bondMapper,
                                  BondRepository bondRepository,
                                  BondRateHistoryRepository rateHistoryRepository,
                                  MarketCacheService<Bond> bondCacheService,
                                  TransactionTemplate transactionTemplate,
                                  BondRateFetcher rateFetcher,
                                  AssetRegistryService assetRegistry,
                                  BondProperties bondProperties) {
        this.bondMapper = bondMapper;
        this.bondRepository = bondRepository;
        this.rateHistoryRepository = rateHistoryRepository;
        this.bondCacheService = bondCacheService;
        this.transactionTemplate = transactionTemplate;
        this.rateFetcher = rateFetcher;
        this.assetRegistry = assetRegistry;
        this.auctionThreshold = bondProperties.getAuctionThreshold();
        this.cpiFixedThreshold = bondProperties.getCpiFixedThreshold();
        this.faceValue = bondProperties.getFaceValue();
        this.daysInYear = bondProperties.getDaysInYear();
    }

    public void processBatch(List<BondSnapshotDto> batch, LocalDateTime now) {
        if (batch == null || batch.isEmpty()) return;
        LocalDate today = LocalDate.now();

        Map<String, Bond> bondsBySeriesCode = new LinkedHashMap<>();
        for (BondSnapshotDto dto : batch) {
            try {
                bondsBySeriesCode.put(dto.seriesCode(), upsertSnapshot(dto, now));
            } catch (Exception e) {
                log.error("Bond upsert failed for {}: {}", dto.seriesCode(), e.getMessage(), e);
            }
        }

        List<BondRateFetcher.BondHistoryTarget> targets = new ArrayList<>();
        for (BondSnapshotDto dto : batch) {
            Bond bond = bondsBySeriesCode.get(dto.seriesCode());
            if (bond == null) continue;
            if (bond.getCouponRate() == null || bond.getCouponRate().compareTo(BigDecimal.ZERO) == 0) continue;
            Optional<BondRateHistory> latestOpt = rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc(dto.isinCode());
            LocalDate startDate;
            if (latestOpt.isEmpty()) {
                if (dto.maturityStart() == null) {
                    log.debug("Skipping rate fetch for {} (no maturityStart)", dto.isinCode());
                    continue;
                }
                startDate = dto.maturityStart();
            } else {
                startDate = latestOpt.get().getRateDate().plusDays(1);
            }
            if (!startDate.isBefore(today)) continue;
            targets.add(new BondRateFetcher.BondHistoryTarget(dto.isinCode(), dto.seriesCode(), bond, startDate, today));
        }

        Map<String, List<BondRateHistory>> fetched = targets.isEmpty()
                ? Map.of()
                : rateFetcher.fetchBatch(targets);

        for (BondSnapshotDto dto : batch) {
            Bond bond = bondsBySeriesCode.get(dto.seriesCode());
            if (bond == null) continue;
            try {
                List<BondRateHistory> records = new ArrayList<>(fetched.getOrDefault(dto.isinCode(), List.of()));
                if (bond.getCouponRate() != null && bond.getCouponRate().compareTo(BigDecimal.ZERO) != 0) {
                    addTodayFromSnapshotIfMissing(records, dto, bond, today);
                }
                if (!records.isEmpty()) {
                    transactionTemplate.executeWithoutResult(status -> rateHistoryRepository.saveAll(records));
                }
                applyClassification(bond, dto);
                transactionTemplate.executeWithoutResult(status -> bondRepository.save(bond));
                bondCacheService.putSnapshot(bond.getSeriesCode(), bond);
            } catch (Exception e) {
                log.error("Bond finalize failed for {}: {}", dto.seriesCode(), e.getMessage(), e);
            }
        }
    }

    private Bond upsertSnapshot(BondSnapshotDto dto, LocalDateTime now) {
        Optional<Bond> existingOpt = bondRepository.findById(dto.seriesCode());
        Bond bond;
        if (existingOpt.isPresent()) {
            bond = existingOpt.get();
            bondMapper.updateEntity(bond, dto, now);
        } else {
            bond = bondMapper.toEntity(dto, now);
            bond.setIssuer("HAZİNE");
        }
        bond.resolveNextCouponDate();
        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);
        bond.setAsset(assetRegistry.upsert(MarketType.BOND, dto.seriesCode()));
        return transactionTemplate.execute(status -> bondRepository.save(bond));
    }

    /** Resolves bond type and simple yield from full rate history; clears coupon date for discount bonds. */
    private void applyClassification(Bond bond, BondSnapshotDto dto) {
        List<BondRateHistory> fullHistory = rateHistoryRepository.findByIsinCodeOrderByRateDateAsc(dto.isinCode());
        bond.resolveType(fullHistory, auctionThreshold, cpiFixedThreshold);
        bond.resolveSimpleYield(faceValue, daysInYear);
        if (bond.isDiscounted()) {
            bond.setNextCouponDate(null);
        }
        log.info("Bond {} processed: type={}, yield={}, rateHistory={} records",
                dto.isinCode(), bond.getBondType(), bond.getSimpleYield(), fullHistory.size());
    }

    private void addTodayFromSnapshotIfMissing(List<BondRateHistory> records, BondSnapshotDto dto, Bond bond,
                                                LocalDate today) {
        if (dto.couponRate() == null) return;
        boolean todayExists = records.stream().anyMatch(r -> today.equals(r.getRateDate()));
        if (todayExists) return;
        if (rateHistoryRepository.existsByIsinCodeAndRateDate(dto.isinCode(), today)) return;
        records.add(BondRateHistory.builder()
                .bond(bond)
                .rateDate(today)
                .couponRate(dto.couponRate())
                .price(dto.cleanPrice())
                .build());
    }
}
