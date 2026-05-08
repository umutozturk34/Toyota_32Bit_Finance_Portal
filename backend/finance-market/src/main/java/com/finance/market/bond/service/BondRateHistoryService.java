package com.finance.market.bond.service;
import com.finance.market.core.cache.MarketCacheService;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.common.exception.BusinessException;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public void processSingleBond(BondSnapshotDto dto, LocalDateTime now) {
        Bond bond = upsertSnapshot(dto, now);
        List<BondRateHistory> newRateRecords = collectRateRecords(dto, bond);
        if (!newRateRecords.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> rateHistoryRepository.saveAll(newRateRecords));
        }
        applyClassification(bond, dto);
        transactionTemplate.executeWithoutResult(status -> bondRepository.save(bond));
        bondCacheService.putSnapshot(bond.getSeriesCode(), bond);
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
        bond.setAsset(assetRegistry.upsert(MarketType.BOND, dto.seriesCode(), bond.getName()));
        return transactionTemplate.execute(status -> bondRepository.save(bond));
    }

    private List<BondRateHistory> collectRateRecords(BondSnapshotDto dto, Bond bond) {
        boolean zeroCoupon = bond.getCouponRate() == null
                || bond.getCouponRate().compareTo(BigDecimal.ZERO) == 0;
        if (zeroCoupon) {
            log.debug("{} - zero coupon, skipping rate history", dto.isinCode());
            return List.of();
        }
        return buildRateRecords(dto, bond);
    }

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

    private List<BondRateHistory> buildRateRecords(BondSnapshotDto dto, Bond bond) {
        String isinCode = dto.isinCode();
        LocalDate today = LocalDate.now();
        Optional<BondRateHistory> latestOpt = rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc(isinCode);

        if (latestOpt.isEmpty()) {
            if (dto.maturityStart() == null) {
                throw new BusinessException("Bond " + isinCode + " has no maturityStart, cannot fetch rate history");
            }
            log.debug("{} - no history, full fetch from {}", isinCode, dto.maturityStart());
            List<BondRateHistory> fetched = rateFetcher.fetch(isinCode, bond, dto.maturityStart(), today);
            addTodayFromSnapshotIfMissing(fetched, dto, bond, today);
            return fetched;
        }

        LocalDate lastDate = latestOpt.get().getRateDate();
        long gapDays = ChronoUnit.DAYS.between(lastDate, today);

        if (gapDays <= 1) {
            log.debug("{} - snapshot only: lastDate={}, gapDays={}", isinCode, lastDate, gapDays);
            List<BondRateHistory> result = new ArrayList<>();
            addTodayFromSnapshotIfMissing(result, dto, bond, today);
            return result;
        }

        LocalDate fetchStart = lastDate.plusDays(1);
        log.debug("{} - incremental fetch: lastDate={}, gapDays={}, fetchStart={}",
                isinCode, lastDate, gapDays, fetchStart);
        List<BondRateHistory> fetched = rateFetcher.fetch(isinCode, bond, fetchStart, today);
        addTodayFromSnapshotIfMissing(fetched, dto, bond, today);
        return fetched;
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
                .build());
    }
}
