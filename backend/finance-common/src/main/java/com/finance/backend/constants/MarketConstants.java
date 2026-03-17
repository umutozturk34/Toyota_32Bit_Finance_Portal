package com.finance.backend.constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Component
public class MarketConstants {
    private final List<String> trackedCryptos;
    private final List<String> trackedBistStocks;
    private final List<String> trackedFunds;
    private final Map<String, String> coinGeckoToBinance;
    public MarketConstants(
            @Value("${tracked.cryptos:}") String cryptosEnv,
            @Value("${bist.stocks:}") String bistStocksEnv,
            @Value("${funds.all:}") String fundsAllEnv,
            @Value("${crypto.binance.symbols:}") String binanceSymbolsEnv) {
        this.trackedCryptos = parseList(cryptosEnv);
        this.trackedBistStocks = parseList(bistStocksEnv);
        this.trackedFunds = parseList(fundsAllEnv);
        this.coinGeckoToBinance = parseMap(binanceSymbolsEnv);
    }
    private List<String> parseList(String envValue) {
        if (envValue == null || envValue.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(envValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
    private Map<String, String> parseMap(String envValue) {
        if (envValue == null || envValue.isBlank()) {
            return Collections.emptyMap();
        }
        return Arrays.stream(envValue.split(","))
                .map(String::trim)
                .filter(s -> s.contains(":"))
                .collect(Collectors.toMap(
                        s -> s.substring(0, s.indexOf(':')).trim(),
                        s -> s.substring(s.indexOf(':') + 1).trim()
                ));
    }
    public List<String> getTrackedCryptos() {
        return trackedCryptos;
    }
    public List<String> getTrackedBistStocks() {
        return trackedBistStocks;
    }
    public List<String> getTrackedFunds() {
        return trackedFunds;
    }
    public String getBinanceSymbol(String coinGeckoId) {
        return coinGeckoToBinance.get(coinGeckoId);
    }
}
