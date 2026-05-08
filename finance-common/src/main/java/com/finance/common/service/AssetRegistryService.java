package com.finance.common.service;

import com.finance.common.model.Asset;
import com.finance.common.model.MarketType;
import com.finance.common.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class AssetRegistryService {

    private final AssetRepository repository;

    @Transactional
    public Asset upsert(MarketType marketType, String assetCode, String displayName) {
        return repository.findByMarketTypeAndAssetCodeIgnoreCase(marketType, assetCode)
                .map(existing -> updateDisplayName(existing, displayName))
                .orElseGet(() -> repository.save(Asset.create(marketType, assetCode, displayName)));
    }

    @Transactional(readOnly = true)
    public Asset requireOne(MarketType marketType, String assetCode) {
        return repository.findByMarketTypeAndAssetCodeIgnoreCase(marketType, assetCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Asset not registered marketType=" + marketType + " code=" + assetCode));
    }

    private Asset updateDisplayName(Asset existing, String displayName) {
        if (displayName != null && !displayName.equals(existing.getDisplayName())) {
            existing.setDisplayName(displayName);
        }
        return existing;
    }
}
