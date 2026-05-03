package com.finance.backend.util;

import com.finance.backend.model.BaseAsset;
import com.finance.backend.model.BaseCandle;

import java.math.BigDecimal;
import java.util.List;

public final class ChangeFromCandlesUpdater {

    private ChangeFromCandlesUpdater() {}

    public static boolean applyFromTopTwoDescIfMissing(BaseAsset asset,
                                                       BigDecimal currentPrice,
                                                       List<? extends BaseCandle> topTwoDesc,
                                                       int scale) {
        if (asset.getChangePercent() != null) return false;
        if (currentPrice == null || topTwoDesc.size() < 2) return false;
        BigDecimal previousClose = topTwoDesc.get(1).getClose();
        if (previousClose == null) return false;
        asset.applyChange(currentPrice, previousClose, scale);
        return true;
    }
}
