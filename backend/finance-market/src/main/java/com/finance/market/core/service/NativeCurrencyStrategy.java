package com.finance.market.core.service;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;

import java.util.Set;

/**
 * Per-market rule for the currency an asset's native price is quoted in (e.g. CRYPTO=USD,
 * FOREX=the pair currency, STOCK/FUND/BOND/MACRO=TRY, VIOP=symbol-derived). One implementation
 * owns one or more {@link MarketType}s and is dispatched by {@link AssetNativeCurrencyResolver}.
 */
public interface NativeCurrencyStrategy {

    /** Market types this strategy is authoritative for. */
    Set<MarketType> supports();

    /** Native quote currency for the given asset code; code-sensitive for VIOP and tether-quoted crypto. */
    Currency resolve(String code);
}
