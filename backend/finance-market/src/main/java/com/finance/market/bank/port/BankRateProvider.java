package com.finance.market.bank.port;

import com.finance.market.bank.dto.BankRateSnapshot;

import java.util.List;

/**
 * Source-agnostic port for bank-by-bank exchange rate snapshots. One implementation per data
 * source (doviz.com, TCMB, custom scraper). The scheduler fans out to all registered providers.
 */
public interface BankRateProvider {

    /** Identifier persisted into {@code bank_exchange_rates.source}. */
    String sourceId();

    /** Fetches a full snapshot across all banks × currencies/golds this provider tracks. */
    List<BankRateSnapshot> fetchAll();
}
