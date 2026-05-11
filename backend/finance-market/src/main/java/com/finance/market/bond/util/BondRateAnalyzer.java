package com.finance.market.bond.util;


import com.finance.market.bond.model.BondRateHistory;

import java.math.BigDecimal;
import java.util.List;

public final class BondRateAnalyzer {

    private BondRateAnalyzer() {}

    public static boolean hasRateChanges(List<BondRateHistory> history) {
        if (history == null || history.size() < 2) return false;

        BigDecimal firstRate = history.getFirst().getCouponRate();
        if (firstRate == null) return false;

        for (int i = 1; i < history.size(); i++) {
            BigDecimal rate = history.get(i).getCouponRate();
            if (rate == null) continue;
            if (rate.compareTo(firstRate) != 0) return true;
        }
        return false;
    }
}
