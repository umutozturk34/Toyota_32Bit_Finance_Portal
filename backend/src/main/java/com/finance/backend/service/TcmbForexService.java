package com.finance.backend.service;
import com.finance.backend.client.TcmbForexClient;
import com.finance.backend.dto.external.TcmbRateDto;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.repository.ForexRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
@Slf4j
public class TcmbForexService {
    private final TcmbForexClient tcmbForexClient;
    private final ForexMapper forexMapper;
    private final ForexRepository forexRepository;
    public TcmbForexService(TcmbForexClient tcmbForexClient,
                            ForexMapper forexMapper,
                            ForexRepository forexRepository) {
        this.tcmbForexClient = tcmbForexClient;
        this.forexMapper = forexMapper;
        this.forexRepository = forexRepository;
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
        log.info("Saved {} forex rates from TCMB", saved.size());
        return saved;
    }
}
