package com.finance.backend.service;


public interface PortfolioSnapshotPort {
    void onMarketUpdate(String assetType);
}
