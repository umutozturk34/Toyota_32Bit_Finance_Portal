package com.finance.market.bank.repository;

import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for per-bank exchange/asset rate quotes. Provides lookup of a single bank's quote for
 * a currency, listings grouped by currency or asset kind, and the set of currencies offered for a
 * given asset kind.
 */
public interface BankExchangeRateRepository extends JpaRepository<BankExchangeRate, Long> {

    /**
     * Finds the unique quote identified by its natural key (data source, bank, currency), used for
     * idempotent upserts when refreshing rates.
     */
    Optional<BankExchangeRate> findBySourceAndBankCodeAndCurrencyCode(
            String source, String bankCode, String currencyCode);

    /** Returns every bank's quote for one currency, ordered alphabetically by bank name (comparison table). */
    List<BankExchangeRate> findByCurrencyCodeOrderByBankNameAsc(String currencyCode);

    /** Returns all quotes for one asset kind (e.g. cash, gold), ordered by currency then bank name. */
    List<BankExchangeRate> findByAssetKindOrderByCurrencyCodeAscBankNameAsc(BankRateAssetKind assetKind);

    /** Returns the distinct currency codes that have at least one quote for the given asset kind, sorted ascending. */
    @Query("SELECT DISTINCT r.currencyCode FROM BankExchangeRate r WHERE r.assetKind = :kind ORDER BY r.currencyCode ASC")
    List<String> findDistinctCurrencyCodesByAssetKind(@Param("kind") BankRateAssetKind kind);
}
