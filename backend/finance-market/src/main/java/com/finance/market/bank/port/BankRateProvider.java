package com.finance.market.bank.port;

import com.finance.market.bank.dto.BankRateSnapshot;

import java.util.List;

/** Source of per-bank currency/gold buy-sell rates; one implementation per upstream provider. */
public interface BankRateProvider {

    /** Stable identifier of this source (stored on each row). */
    String sourceId();

    /** All currently available bank rate snapshots from this source. */
    List<BankRateSnapshot> fetchAll();
}
