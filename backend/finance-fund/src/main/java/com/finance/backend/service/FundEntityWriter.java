package com.finance.backend.service;

import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TrackedAssetUpsertCommand;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Log4j2
@Component
@RequiredArgsConstructor
public class FundEntityWriter {

    private static final int AUTO_TRACK_SORT_ORDER = 9999;

    private final FundRepository fundRepository;
    private final FundMapper fundMapper;
    private final FundChangeCalculator fundChangeCalculator;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;

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

    public void ensureByfTracked(String fundCode, String tefasName) {
        if (trackedAssetQueryService.getTrackedAsset(TrackedAssetType.FUND, fundCode).isPresent()) {
            return;
        }
        trackedAssetCommandService.upsert(TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.FUND)
                .assetCode(fundCode)
                .displayName(tefasName)
                .enabled(true)
                .sortOrder(AUTO_TRACK_SORT_ORDER)
                .build());
        log.info("Auto-added BYF fund to tracked assets: {}", fundCode);
    }
}
