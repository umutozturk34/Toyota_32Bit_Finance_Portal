package com.finance.market.bank.port;

import com.finance.market.bank.dto.BankRateSnapshot;

import java.util.List;

public interface BankRateProvider {

    String sourceId();

    List<BankRateSnapshot> fetchAll();
}
