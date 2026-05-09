package com.finance.market.core.service;

public interface MarketSnapshotProcessor {

    void refreshOne(String code);

    boolean exists(String code);
}
