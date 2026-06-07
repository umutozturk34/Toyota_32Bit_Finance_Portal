package com.finance.market.commodity.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Externalized configuration ({@code app.commodity.*}) for commodity market data.
 *
 * <p>Defines the Yahoo chart fetch window ({@code chartRange}/{@code chartInterval}),
 * the minimum sample size for batch processing, and the sort order applied to
 * synthetic derivative instruments. {@code yahooSymbolOverrides} maps internal
 * commodity codes to Yahoo tickers (e.g. {@code XAUTRY} → {@code GC=F}), while
 * {@code derivatives} declares computed instruments such as gram-priced gold/silver
 * derived from an ounce-priced source via {@link DerivativeRule}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.commodity")
public class CommodityProperties {

    private String chartRange = "max";
    private String chartInterval = "1d";
    private int batchMinSample = 5;
    private int derivativeSortOrder = 9999;

    private Map<String, String> yahooSymbolOverrides = new HashMap<>(Map.of(
            "XAUTRY", "GC=F",
            "XAGTRY", "SI=F",
            "XPTTRY", "PL=F",
            "XPDTRY", "PA=F"
    ));

    private List<DerivativeRule> derivatives = new ArrayList<>(List.of(
            new DerivativeRule("XAUTRY", "XAUTRYG", new BigDecimal("31.1035"), "Altın (Gram)", "Gram Altın"),
            new DerivativeRule("XAGTRY", "XAGTRYG", new BigDecimal("31.1035"), "Gümüş (Gram)", "Gram Gümüş")
    ));

    /**
     * Declares a synthetic commodity derived from a source instrument by a fixed
     * divisor (e.g. converting an ounce-priced metal to a gram price). Carries the
     * source/derivative codes, the conversion {@code divisor}, and English/Turkish
     * display names.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DerivativeRule {
        private String sourceCode;
        private String derivativeCode;
        private BigDecimal divisor;
        private String name;
        private String nameTr;
    }
}
