package com.finance.common.service;

public interface MarketSnapshotProcessor {

    void refreshOne(String code);

    boolean exists(String code);
}
