package com.finance.backend.service;

import com.finance.backend.client.MetalsClient;
import com.finance.backend.dto.CoinGeckoResponse;
import com.finance.backend.entity.MetalPrice;
import com.finance.backend.repository.MetalPriceRepository;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetalService {
    
    private final MetalsClient metalsClient;
    private final MetalPriceRepository metalPriceRepository;
    
    private static final Map<String, String> METAL_MAPPING = Map.of(
            "pax-gold", "PAXG",
            "tether-gold", "XAUT",
            "kinesis-silver", "KAG"
    );
    
    private static final Map<String, String> METAL_DISPLAY_NAMES = Map.of(
            "pax-gold", "PAX Gold (Altın)",
            "tether-gold", "Tether Gold (Altın)",
            "kinesis-silver", "Kinesis Silver (Gümüş)"
    );
    
    public List<MetalPrice> getLatestPrices() {
        return metalPriceRepository.findLatestPrices();
    }
    
    public Page<MetalPrice> getLatestPrices(Pageable pageable) {
        return metalPriceRepository.findLatestPrices(pageable);
    }
    
    public List<MetalPrice> getMetalHistory(String symbol) {
        return metalPriceRepository.findBySymbolOrderByTimestampDesc(symbol);
    }
    
    @Scheduled(fixedRate = 43200000)
    @Transactional
    public void fetchAndStorePreciousMetals() {
        log.info("Fetching precious metals");
        
        try {
            List<CoinGeckoResponse> metals = metalsClient.fetchPreciousMetals();
            
            if (metals == null || metals.isEmpty()) {
                log.warn("No precious metals data received");
                return;
            }
            
            log.info("Processing {} metals from CoinGecko", metals.size());
            
            for (CoinGeckoResponse metal : metals) {
                if (metal != null && metal.getId() != null && METAL_MAPPING.containsKey(metal.getId())) {
                    MetalPrice metalPrice = new MetalPrice();
                    metalPrice.setSymbol(METAL_MAPPING.get(metal.getId()));
                    metalPrice.setName(METAL_DISPLAY_NAMES.getOrDefault(metal.getId(), metal.getName()));
                    metalPrice.setPriceUsd(BigDecimal.valueOf(metal.getCurrentPrice() != null ? metal.getCurrentPrice() : 0));
                    metalPrice.setChangeAmount(BigDecimal.valueOf(metal.getPriceChange24h() != null ? metal.getPriceChange24h() : 0));
                    metalPrice.setChangePercent(BigDecimal.valueOf(metal.getPriceChangePercentage24h() != null ? metal.getPriceChangePercentage24h() : 0));
                    metalPrice.setTimestamp(LocalDateTime.now());
                    
                    metalPriceRepository.save(metalPrice);
                    log.info("Saved precious metal: {} ({}) at ${}", metalPrice.getSymbol(), metalPrice.getName(), metalPrice.getPriceUsd());
                } else {
                    log.debug("Skipping metal with id: {}", metal != null ? metal.getId() : "null");
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing precious metals: {}", e.getMessage(), e);
        }
    }
}