package com.finance.backend.service;
import com.finance.backend.client.TcmbForexClient;
import com.finance.backend.dto.external.TcmbRateDto;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexRepository;
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
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    public TcmbForexService(TcmbForexClient tcmbForexClient,
                            ForexMapper forexMapper,
                            ForexRepository forexRepository,
                            MarketCacheService<Forex, ForexCandle> forexCacheService) {
        this.tcmbForexClient = tcmbForexClient;
        this.forexMapper = forexMapper;
        this.forexRepository = forexRepository;
        this.forexCacheService = forexCacheService;
    }
    @Transactional
    public List<Forex> fetchAndSaveTcmbRates() {
        List<TcmbRateDto> rates = tcmbForexClient.fetchDailyRates();
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
            if (existing != null) {
                forexMapper.updateEntity(existing, dto);
                toSave.add(existing);
            } else {
                toSave.add(forexMapper.toEntity(dto));
            }
        }
        List<Forex> saved = forexRepository.saveAll(toSave);
        for (Forex forex : saved) {
            forexCacheService.putSnapshot(forex.getCurrencyCode(), forex);
        }
        log.info("Saved {} forex rates from TCMB", saved.size());
        return saved;
    }
}
