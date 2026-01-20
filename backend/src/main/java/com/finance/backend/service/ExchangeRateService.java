package com.finance.backend.service;

import com.finance.backend.client.TcmbApiClient;
import com.finance.backend.entity.ExchangeRate;
import com.finance.backend.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {
    
    private final TcmbApiClient tcmbApiClient;
    private final ExchangeRateRepository exchangeRateRepository;
    
    public List<ExchangeRate> getLatestRates() {
        return exchangeRateRepository.findLatestRates();
    }
    
    public Optional<ExchangeRate> getRateByCurrency(String currencyCode, LocalDate date) {
        return exchangeRateRepository.findByCurrencyCodeAndRateDate(currencyCode, date);
    }
    
    public List<ExchangeRate> getRateHistory(String currencyCode) {
        return exchangeRateRepository.findByCurrencyCodeOrderByRateDateDesc(currencyCode);
    }
    
    @Transactional
    @Scheduled(cron = "0 0 15 * * ?")
    public void fetchAndStoreRates() {
        log.info("Starting scheduled exchange rate fetch");
        
        try {
            List<ExchangeRate> rates = tcmbApiClient.fetchExchangeRates();
            
            if (rates.isEmpty()) {
                log.warn("No exchange rates received from TCMB");
                return;
            }
            
            int savedCount = 0;
            for (ExchangeRate rate : rates) {
                Optional<ExchangeRate> existing = exchangeRateRepository
                        .findByCurrencyCodeAndRateDate(rate.getCurrencyCode(), rate.getRateDate());
                
                if (existing.isEmpty()) {
                    exchangeRateRepository.save(rate);
                    savedCount++;
                }
            }
            
            log.info("Saved {} new exchange rates to database", savedCount);
            
        } catch (Exception e) {
            log.error("Error during scheduled exchange rate fetch: {}", e.getMessage(), e);
        }
    }
}
