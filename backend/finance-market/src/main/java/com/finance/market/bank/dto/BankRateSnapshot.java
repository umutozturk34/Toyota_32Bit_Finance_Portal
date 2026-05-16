package com.finance.market.bank.dto;

import com.finance.market.bank.model.BankRateAssetKind;

import java.math.BigDecimal;

/**
 * Provider-agnostic snapshot of a single bank's rate for a given currency/gold. Persisted into
 * {@code bank_exchange_rates}; one row per (source, bankCode, currencyCode).
 */
public record BankRateSnapshot(
        String source,
        String bankCode,
        String bankName,
        String bankLogoUrl,
        String currencyCode,
        String currencyName,
        BankRateAssetKind assetKind,
        BigDecimal buyRate,
        BigDecimal sellRate
) { }
