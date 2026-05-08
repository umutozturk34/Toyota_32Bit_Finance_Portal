package com.finance.forex.service;
import com.finance.cache.service.MarketCacheService;

import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;
import com.finance.forex.client.TcmbForexClient;
import com.finance.forex.dto.external.TcmbRateDto;
import com.finance.forex.mapper.ForexMapper;
import com.finance.forex.model.Forex;
import com.finance.forex.repository.ForexRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
@Log4j2
public class TcmbForexService {
    private final TcmbForexClient tcmbForexClient;
    private final ForexMapper forexMapper;
    private final ForexRepository forexRepository;
    private final MarketCacheService<Forex> forexCacheService;
    private final AssetRegistryService assetRegistry;
    public TcmbForexService(TcmbForexClient tcmbForexClient,
                            ForexMapper forexMapper,
                            ForexRepository forexRepository,
                            MarketCacheService<Forex> forexCacheService,
                            AssetRegistryService assetRegistry) {
        this.tcmbForexClient = tcmbForexClient;
        this.forexMapper = forexMapper;
        this.forexRepository = forexRepository;
        this.forexCacheService = forexCacheService;
        this.assetRegistry = assetRegistry;
    }
    @Transactional
    public List<Forex> fetchAndSaveTcmbRates() {
        List<TcmbRateDto> rates = tcmbForexClient.fetchDailyRates();
        log.debug("Fetched {} rates from TCMB, processing...", rates.size());
        List<String> codes = rates.stream()
                .map(dto -> forexMapper.toCurrencyPairCode(dto.currencyCode()))
                .toList();
        Map<String, Forex> existingMap = forexRepository.findAllById(codes)
                .stream()
                .collect(Collectors.toMap(Forex::getCurrencyCode, Function.identity()));
        List<Forex> toSave = new ArrayList<>(rates.size());
        for (TcmbRateDto dto : rates) {
            String pairCode = forexMapper.toCurrencyPairCode(dto.currencyCode());
            Forex existing = existingMap.get(pairCode);
            Forex toPersist;
            if (existing != null) {
                forexMapper.updateEntity(existing, dto);
                toPersist = existing;
            } else {
                toPersist = forexMapper.toEntity(dto);
            }
            toPersist.setAsset(assetRegistry.upsert(MarketType.FOREX, pairCode, toPersist.getCurrencyName()));
            toSave.add(toPersist);
        }
        List<Forex> saved = forexRepository.saveAll(toSave);
        for (Forex forex : saved) {
            forexCacheService.putSnapshot(forex.getCurrencyCode(), forex);
        }
        log.info("Saved {} forex rates from TCMB", saved.size());
        return saved;
    }
}
