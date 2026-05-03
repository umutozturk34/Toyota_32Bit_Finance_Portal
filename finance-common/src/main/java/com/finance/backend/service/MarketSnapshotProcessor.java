package com.finance.backend.service;

public interface MarketSnapshotProcessor {

    void refreshOne(String code);

    boolean exists(String code);
}
