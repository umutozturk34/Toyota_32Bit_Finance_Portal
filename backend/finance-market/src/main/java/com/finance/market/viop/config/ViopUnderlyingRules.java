package com.finance.market.viop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The underlying codes that drive VIOP category classification (index / currency / metal); everything else falls back
 * to PAY. Externalised from {@code ViopCategoryResolver} so the listing can be tuned as BIST adds new contracts
 * without a recompile. The compact constructor supplies the production set for any unset list.
 */
@ConfigurationProperties(prefix = "app.market.viop-underlyings")
public record ViopUnderlyingRules(
        List<String> indexCodes,
        List<String> currencyCodes,
        List<String> metalCodes
) {

    public ViopUnderlyingRules {
        if (indexCodes == null || indexCodes.isEmpty()) {
            indexCodes = List.of("XU030", "XLBNK", "X10XB", "XSD25", "XBNK", "XU100");
        }
        if (currencyCodes == null || currencyCodes.isEmpty()) {
            currencyCodes = List.of("USDTRY", "EURTRY", "RUBTRY", "CNHTRY", "GBPTRY", "JPYTRY", "EURUSD", "GBPUSD", "USDJPY");
        }
        if (metalCodes == null || metalCodes.isEmpty()) {
            metalCodes = List.of("XAU", "XAG", "XPT", "XPD", "XCU");
        }
    }
}
