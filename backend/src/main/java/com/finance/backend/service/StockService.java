package com.finance.backend.service;

import com.finance.backend.client.AlphaVantageClient;
import com.finance.backend.client.CollectApiClient;
import com.finance.backend.client.IsYatirimClient;
import com.finance.backend.client.TwelveDataClient;
import com.finance.backend.dto.AlphaVantageResponse;
import com.finance.backend.dto.StockPriceDto;
import com.finance.backend.entity.StockPrice;
import com.finance.backend.repository.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {
    
    private final StockPriceRepository stockPriceRepository;
    private final AlphaVantageClient alphaVantageClient;
    private final TwelveDataClient twelveDataClient;
    private final IsYatirimClient isYatirimClient;
    private final CollectApiClient collectApiClient;
    
    private static final List<String> US_SYMBOLS = Arrays.asList(
            "AAPL", "MSFT", "NVDA", "TSLA", "AMZN",
            "META", "GOOGL", "AMD", "INTC", "NFLX",
            "JPM", "BAC", "V", "MA", "GS",
            "XOM", "WMT", "JNJ", "KO", "DIS"
    );
    
    @Scheduled(fixedRate = 43200000)
    @Transactional
    public void fetchAndStoreUsStocks() {
        log.info("Scheduled task: Fetching US stocks");
        boolean success = fetchUsStocksFromTwelveData();
        
        if (!success) {
            log.warn("Twelve Data failed, trying AlphaVantage as fallback...");
            fetchUsStocksFromAlphaVantage();
        }
    }
    
    private boolean fetchUsStocksFromTwelveData() {
        log.info("Fetching US stocks from Twelve Data API...");
        int savedCount = 0;
        
        try {
            for (int i = 0; i < US_SYMBOLS.size(); i += 8) {
                List<String> batch = US_SYMBOLS.subList(i, Math.min(i + 8, US_SYMBOLS.size()));
                
                Map<String, TwelveDataClient.StockQuote> quotes = twelveDataClient.fetchMultipleQuotes(batch);
                
                for (TwelveDataClient.StockQuote quote : quotes.values()) {
                    if (quote != null && quote.getClose() != null) {
                        StockPrice stock = new StockPrice();
                        stock.setSymbol(quote.getSymbol());
                        stock.setName(quote.getName() != null ? quote.getName() : quote.getSymbol());
                        stock.setMarket("US");
                        stock.setPrice(BigDecimal.valueOf(quote.getClose()));
                        
                        if (quote.getChange() != null) {
                            stock.setChangeAmount(BigDecimal.valueOf(quote.getChange()));
                        }
                        if (quote.getPercentChange() != null) {
                            stock.setChangePercent(BigDecimal.valueOf(quote.getPercentChange()));
                        }
                        if (quote.getOpen() != null) {
                            stock.setOpen(BigDecimal.valueOf(quote.getOpen()));
                        }
                        if (quote.getHigh() != null) {
                            stock.setHigh(BigDecimal.valueOf(quote.getHigh()));
                        }
                        if (quote.getLow() != null) {
                            stock.setLow(BigDecimal.valueOf(quote.getLow()));
                        }
                        if (quote.getVolume() != null) {
                            stock.setVolume(quote.getVolume());
                        }
                        stock.setTimestamp(LocalDateTime.now());
                        
                        stockPriceRepository.save(stock);
                        savedCount++;
                        log.info("Saved US stock from Twelve Data: {} at ${}", quote.getSymbol(), quote.getClose());
                    }
                }
                
                if (i + 8 < US_SYMBOLS.size()) {
                    log.info("Waiting 65 seconds for rate limit...");
                    Thread.sleep(65000);
                }
            }
            
            log.info("Successfully saved {} US stocks from Twelve Data", savedCount);
            return savedCount > 0;
            
        } catch (Exception e) {
            log.error("Error fetching from Twelve Data: {}", e.getMessage());
            return false;
        }
    }
    
    private void fetchUsStocksFromAlphaVantage() {
        log.info("Fetching US stocks from AlphaVantage (fallback)...");
        int savedCount = 0;
        
        for (String symbol : US_SYMBOLS) {
            try {
                AlphaVantageResponse response = alphaVantageClient.fetchStockQuote(symbol);
                
                if (response != null && response.getGlobalQuote() != null) {
                    AlphaVantageResponse.GlobalQuote quote = response.getGlobalQuote();
                    
                    StockPrice stock = new StockPrice();
                    stock.setSymbol(quote.getSymbol());
                    stock.setName(quote.getSymbol()); // API doesn't provide name
                    stock.setMarket("US");
                    stock.setPrice(new BigDecimal(quote.getPrice()));
                    stock.setChangeAmount(new BigDecimal(quote.getChange()));
                    
                    // Parse change percent (remove % sign)
                    String changePercent = quote.getChangePercent().replace("%", "");
                    stock.setChangePercent(new BigDecimal(changePercent));
                    
                    stock.setOpen(new BigDecimal(quote.getOpen()));
                    stock.setHigh(new BigDecimal(quote.getHigh()));
                    stock.setLow(new BigDecimal(quote.getLow()));
                    stock.setVolume(Long.parseLong(quote.getVolume()));
                    stock.setTimestamp(LocalDateTime.now());
                    
                    stockPriceRepository.save(stock);
                    savedCount++;
                    log.info("Saved US stock from AlphaVantage: {}", symbol);
                } else {
                    log.warn("No data from AlphaVantage for {}", symbol);
                }
                
                Thread.sleep(12000);
                
            } catch (Exception e) {
                log.error("Error processing US stock {}: {}", symbol, e.getMessage());
            }
        }
        
        log.info("Saved {} US stocks from AlphaVantage", savedCount);
    }
    
    // BIST stocks via CollectAPI (PRIMARY) - günde 1 kez
    // Top 20 hisse (hacme göre) + Top 10 GYO (fon)
    @Scheduled(cron = "0 0 9 * * MON-FRI") // Hafta içi sabah 9'da (piyasa açılışı)
    @Transactional
    public void fetchAndStoreBISTStocksFromCollectAPI() {
        log.info("Scheduled task: Fetching BIST top 20 stocks + top 10 funds from CollectAPI");
        
        try {
            List<CollectApiClient.LiveBorsaItem> allStocks = collectApiClient.fetchLiveBorsa();
            
            if (allStocks == null || allStocks.isEmpty()) {
                log.warn("No BIST stocks fetched from CollectAPI. Trying İş Yatırım fallback...");
                fetchAndStoreBISTStocksFromIsYatirim();
                return;
            }
            
            List<CollectApiClient.LiveBorsaItem> gyoStocks = allStocks.stream()
                    .filter(s -> s.getName() != null && s.getName().endsWith("GYO"))
                    .sorted((a, b) -> parseVolume(b.getHacimtl()).compareTo(parseVolume(a.getHacimtl())))
                    .limit(10)
                    .toList();
            
            List<CollectApiClient.LiveBorsaItem> regularStocks = allStocks.stream()
                    .filter(s -> s.getName() != null 
                            && !s.getName().endsWith("GYO") 
                            && !s.getName().endsWith("YAT")
                            && !s.getName().startsWith("X"))
                    .sorted((a, b) -> parseVolume(b.getHacimtl()).compareTo(parseVolume(a.getHacimtl())))
                    .limit(20)
                    .toList();
            
            int savedStocks = 0;
            int savedFunds = 0;
            
            for (CollectApiClient.LiveBorsaItem item : regularStocks) {
                try {
                    saveStockFromCollectAPI(item, "BIST");
                    savedStocks++;
                } catch (Exception e) {
                    log.debug("Error processing stock {}: {}", item.getName(), e.getMessage());
                }
            }
            
            for (CollectApiClient.LiveBorsaItem item : gyoStocks) {
                try {
                    saveStockFromCollectAPI(item, "BIST-FUND");
                    savedFunds++;
                } catch (Exception e) {
                    log.debug("Error processing fund {}: {}", item.getName(), e.getMessage());
                }
            }
            
            log.info("Successfully saved {} BIST stocks and {} BIST funds from CollectAPI", savedStocks, savedFunds);
            
        } catch (Exception e) {
            log.error("Error fetching BIST stocks from CollectAPI: {}", e.getMessage());
            fetchAndStoreBISTStocksFromIsYatirim();
        }
    }
    
    private Long parseVolume(String volumeStr) {
        if (volumeStr == null) return 0L;
        try {
            return Long.parseLong(volumeStr.replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
    
    private void saveStockFromCollectAPI(CollectApiClient.LiveBorsaItem item, String market) {
        StockPrice stock = new StockPrice();
        stock.setSymbol(item.getName());
        stock.setName(item.getName());
        stock.setMarket(market);
        stock.setPrice(item.getPrice() != null ? BigDecimal.valueOf(item.getPrice()) : BigDecimal.ZERO);
        stock.setChangePercent(item.getRate() != null ? BigDecimal.valueOf(item.getRate()) : BigDecimal.ZERO);
        stock.setVolume(parseVolume(item.getHacimlot()));
        stock.setTimestamp(LocalDateTime.now());
        stockPriceRepository.save(stock);
    }
    
    @Scheduled(cron = "0 30 9 * * MON-FRI")
    @Transactional
    public void fetchAndStoreBISTStocksFromIsYatirim() {
        log.info("Fetching BIST stocks from İş Yatırım (fallback)");
        
        try {
            List<StockPriceDto> stocks = isYatirimClient.fetchAllBistStocks();
            
            if (stocks == null || stocks.isEmpty()) {
                log.warn("No BIST stocks fetched from İş Yatırım");
                return;
            }
            
            for (StockPriceDto dto : stocks) {
                StockPrice bistStock = new StockPrice();
                bistStock.setSymbol(dto.getSymbol());
                bistStock.setName(dto.getName());
                bistStock.setMarket("BIST");
                bistStock.setPrice(dto.getPrice());
                bistStock.setChangeAmount(dto.getChangeAmount());
                bistStock.setChangePercent(dto.getChangePercent());
                bistStock.setOpen(dto.getOpen());
                bistStock.setHigh(dto.getHigh());
                bistStock.setLow(dto.getLow());
                bistStock.setVolume(dto.getVolume());
                bistStock.setTimestamp(dto.getTimestamp());
                
                stockPriceRepository.save(bistStock);
            }
            
            log.info("Successfully saved {} BIST stocks from İş Yatırım", stocks.size());
            
        } catch (Exception e) {
            log.error("Error fetching BIST stocks from İş Yatırım: {}", e.getMessage());
        }
    }
    
    public Page<StockPrice> getLatestStocks(Pageable pageable) {
        return stockPriceRepository.findLatestPrices(pageable);
    }
    
    public Page<StockPrice> getLatestStocksByMarket(String market, Pageable pageable) {
        return stockPriceRepository.findLatestPricesByMarket(market, pageable);
    }
    
    public StockPrice getLatestStockBySymbol(String symbol) {
        return stockPriceRepository.findFirstBySymbolOrderByTimestampDesc(symbol).orElse(null);
    }
    
    public List<StockPrice> getStockHistory(String symbol) {
        return stockPriceRepository.findBySymbolOrderByTimestampDesc(symbol);
    }
    
    public com.finance.backend.dto.BistIndexDto getBistIndex() {
        CollectApiClient.BistIndexItem index = collectApiClient.fetchBistIndex();
        if (index == null) {
            return null;
        }
        return com.finance.backend.dto.BistIndexDto.builder()
                .name("BIST 100")
                .current(index.getCurrent())
                .changeRate(index.getChangerate())
                .min(index.getMin())
                .max(index.getMax())
                .opening(index.getOpening())
                .closing(index.getClosing())
                .time(index.getTime())
                .date(index.getDate())
                .build();
    }
    
    @PostConstruct
    public void initBISTStocksOnStartup() {
        long bistCount = stockPriceRepository.countByMarket("BIST");
        long fundCount = stockPriceRepository.countByMarket("BIST-FUND");
        
        if (bistCount == 0 || fundCount == 0) {
            log.info("BIST stocks: {}, BIST funds: {}. Fetching from CollectAPI on startup...", bistCount, fundCount);
            fetchAndStoreBISTStocksFromCollectAPI();
        } else {
            log.info("Found {} BIST stocks and {} BIST funds in database. Skipping initial fetch.", bistCount, fundCount);
        }
    }
}
