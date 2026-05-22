package com.finance.market.core.service;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.common.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class AssetRegistryService {

    private final InstrumentRepository repository;

    @Transactional
    public Instrument upsert(MarketType marketType, String assetCode) {
        return repository.findByMarketTypeAndAssetCodeIgnoreCase(marketType, assetCode)
                .orElseGet(() -> repository.save(Instrument.create(marketType, assetCode)));
    }
}
