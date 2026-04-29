package com.finance.backend.service;

import com.finance.backend.config.FundProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TrackedAssetUpsertCommand;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Log4j2
@Component
public class FundEntityWriter implements MarketEntityWriter {

    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final FundMapper fundMapper;
    private final FundChangeCalculator fundChangeCalculator;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final int autoTrackSortOrder;

    public FundEntityWriter(FundRepository fundRepository,
                            FundCandleRepository fundCandleRepository,
                            FundMapper fundMapper,
                            FundChangeCalculator fundChangeCalculator,
                            TrackedAssetQueryService trackedAssetQueryService,
                            TrackedAssetCommandService trackedAssetCommandService,
                            FundProperties fundProperties) {
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.fundMapper = fundMapper;
        this.fundChangeCalculator = fundChangeCalculator;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.trackedAssetCommandService = trackedAssetCommandService;
        this.autoTrackSortOrder = fundProperties.getAutoTrackSortOrder();
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
        toPersist.setChangePercent(fundChangeCalculator.calculateChangePercent(dto.fundCode(), dto.price()));
        fundRepository.save(toPersist);
        log.debug("Saved snapshot: {} ({}) - {}", dto.fundCode(), fundType, dto.price());
        return toPersist;
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
        if (trackedAssetQueryService.getTrackedAsset(TrackedAssetType.FUND, fundCode).isPresent()) {
            return;
        }
        trackedAssetCommandService.upsert(TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.FUND)
                .assetCode(fundCode)
                .displayName(tefasName)
                .enabled(true)
                .sortOrder(autoTrackSortOrder)
                .build());
        log.info("Auto-added BYF fund to tracked assets: {}", fundCode);
    }
}
