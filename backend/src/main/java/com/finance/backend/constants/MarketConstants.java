package com.finance.backend.constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
@Component
public class MarketConstants {
    private final List<String> trackedCryptos;
    private final List<String> trackedBistStocks;
    public MarketConstants(
            @Value("${tracked.cryptos:}") String cryptosEnv,
            @Value("${bist.stocks:}") String bistStocksEnv) {
        this.trackedCryptos = parseList(cryptosEnv);
        this.trackedBistStocks = parseList(bistStocksEnv);
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
    public List<String> getTrackedCryptos() {
        return trackedCryptos;
    }
    public List<String> getTrackedBistStocks() {
        return trackedBistStocks;
    }
}
