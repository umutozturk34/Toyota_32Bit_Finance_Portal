package com.finance.market.viop.service;

import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContractKind;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classifies a VIOP contract into a {@link ViopCategory} from its kind, underlying and currency
 * (index/currency/metal/pay), splitting currency and metal futures into TRY- vs USD-quoted
 * variants so they group and price correctly.
 */
@Component
public class ViopCategoryResolver {

    private static final Set<String> INDEX_UNDERLYINGS = Set.of("XU030", "XLBNK", "X10XB", "XSD25", "XBNK", "XU100");
    private static final Set<String> CURRENCY_UNDERLYINGS = Set.of(
            "USDTRY", "EURTRY", "RUBTRY", "CNHTRY", "GBPTRY", "JPYTRY",
            "EURUSD", "GBPUSD", "USDJPY"
    );
    private static final Set<String> METAL_UNDERLYINGS = Set.of("XAU", "XAG", "XPT", "XPD");

    public ViopCategory resolve(ViopContractKind kind, String underlying, String currency) {
        String normalized = normalize(underlying);
        if (kind == ViopContractKind.OPTION) {
            if (normalized == null) return ViopCategory.PAY_OPTION;
            if (isIndex(normalized)) return ViopCategory.INDEX_OPTION;
            if (isCurrency(normalized)) return ViopCategory.CURRENCY_OPTION;
            return ViopCategory.PAY_OPTION;
        }
        if (normalized == null) return ViopCategory.PAY_FUTURE;
        if (isIndex(normalized)) return ViopCategory.INDEX_FUTURE;
        if (isMetal(normalized)) {
            return isUsdQuoted(normalized, currency) ? ViopCategory.METAL_FUTURE_USD : ViopCategory.METAL_FUTURE_TRY;
        }
        if (isCurrency(normalized)) {
            return isUsdQuoted(normalized, currency) ? ViopCategory.CURRENCY_FUTURE_USD : ViopCategory.CURRENCY_FUTURE_TRY;
        }
        return ViopCategory.PAY_FUTURE;
    }

    /** Strips the {@code D_} prefix and any {@code .}-suffix so underlyings match the known sets. */
    private String normalize(String underlying) {
        if (underlying == null || underlying.isBlank()) return null;
        String u = underlying.trim().toUpperCase();
        if (u.startsWith("D_")) {
            u = u.substring(2);
        }
        int dot = u.indexOf('.');
        if (dot > 0) {
            u = u.substring(0, dot);
        }
        return u;
    }

    private boolean isIndex(String u) {
        return INDEX_UNDERLYINGS.stream().anyMatch(u::startsWith);
    }

    private boolean isCurrency(String u) {
        return CURRENCY_UNDERLYINGS.stream().anyMatch(u::startsWith);
    }

    private boolean isMetal(String u) {
        return METAL_UNDERLYINGS.stream().anyMatch(u::startsWith);
    }

    private boolean isUsdQuoted(String normalizedUnderlying, String currency) {
        return "USD".equalsIgnoreCase(currency) || normalizedUnderlying.endsWith("USD");
    }
}
