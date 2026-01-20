package com.finance.backend.service;

import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.dto.CoinGeckoResponse;
import com.finance.backend.entity.CryptoPrice;
import com.finance.backend.repository.CryptoPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoService {
    
    private final CryptoPriceRepository cryptoPriceRepository;
    private final CoinGeckoClient coinGeckoClient;
    
    private static final BigDecimal USD_TO_TRY = new BigDecimal("34.50");
    
    @Scheduled(fixedRate = 43200000)
    @Transactional
    public void fetchAndStoreCryptoPrices() {
        log.info("Fetching crypto prices");
        
        try {
            List<CoinGeckoResponse> cryptos = coinGeckoClient.fetchTopCryptos();
            
            for (CoinGeckoResponse crypto : cryptos) {
                CryptoPrice cryptoPrice = new CryptoPrice();
                cryptoPrice.setSymbol(crypto.getSymbol().toUpperCase());
                cryptoPrice.setName(crypto.getName());
                cryptoPrice.setPriceUsd(BigDecimal.valueOf(crypto.getCurrentPrice()));
                
                BigDecimal priceTry = BigDecimal.valueOf(crypto.getCurrentPrice()).multiply(USD_TO_TRY);
                cryptoPrice.setPriceTry(priceTry);
                
                cryptoPrice.setChangePercent24h(BigDecimal.valueOf(
                        crypto.getPriceChangePercentage24h() != null ? crypto.getPriceChangePercentage24h() : 0.0
                ));
                
                cryptoPrice.setMarketCapUsd(BigDecimal.valueOf(crypto.getMarketCap()));
                cryptoPrice.setVolume24hUsd(BigDecimal.valueOf(
                        crypto.getTotalVolume() != null ? crypto.getTotalVolume() : 0L
                ));
                cryptoPrice.setMarketCapRank(crypto.getMarketCapRank());
                cryptoPrice.setTimestamp(LocalDateTime.now());
                
                cryptoPriceRepository.save(cryptoPrice);
                log.info("Saved crypto: {}", crypto.getSymbol());
            }
            
            log.info("Successfully saved {} cryptocurrencies", cryptos.size());
            
        } catch (Exception e) {
            log.error("Error processing cryptocurrencies: {}", e.getMessage());
        }
    }
    
    public Page<CryptoPrice> getLatestCryptos(Pageable pageable) {
        return cryptoPriceRepository.findLatestPrices(pageable);
    }
    
    public CryptoPrice getLatestCryptoBySymbol(String symbol) {
        return cryptoPriceRepository.findFirstBySymbolOrderByTimestampDesc(symbol.toUpperCase()).orElse(null);
    }
    
    public List<CryptoPrice> getCryptoHistory(String symbol) {
        return cryptoPriceRepository.findBySymbolOrderByTimestampDesc(symbol.toUpperCase());
    }
}
