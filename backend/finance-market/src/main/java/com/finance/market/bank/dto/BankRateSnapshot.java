package com.finance.market.bank.dto;

import com.finance.market.bank.model.BankRateAssetKind;

import java.math.BigDecimal;

/** Immutable scraped bank rate (one bank, one currency/gold product) before persistence. */
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
