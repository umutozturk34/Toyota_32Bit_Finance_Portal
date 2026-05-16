package com.finance.market.bank.repository;

import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankExchangeRateRepository extends JpaRepository<BankExchangeRate, Long> {

    Optional<BankExchangeRate> findBySourceAndBankCodeAndCurrencyCode(
            String source, String bankCode, String currencyCode);

    List<BankExchangeRate> findByCurrencyCodeOrderByBankNameAsc(String currencyCode);

    List<BankExchangeRate> findByAssetKindOrderByCurrencyCodeAscBankNameAsc(BankRateAssetKind assetKind);

    @Query("SELECT DISTINCT r.currencyCode FROM BankExchangeRate r WHERE r.assetKind = :kind ORDER BY r.currencyCode ASC")
    List<String> findDistinctCurrencyCodesByAssetKind(@Param("kind") BankRateAssetKind kind);
}
