package com.finance.market.bond.util;


import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.List;

/**
 * Classifies a bond into a {@link BondType} from its ISIN prefix (TRB=discounted, TRD=sukuk), base
 * index, coupon rate, and rate-change history, applying the auction/CPI thresholds. A high base index
 * or sub-threshold coupon implies CPI-linked floating; rate changes split auction vs. TLREF floating.
 */
@Log4j2
public final class BondTypeResolver {

    private static final BigDecimal HIGH_BASE_INDEX = new BigDecimal("110");
    private static final String SUKUK_PREFIX = "TRD";
    private static final String DISCOUNTED_PREFIX = "TRB";

    private BondTypeResolver() {
    }

    public static BondType resolve(Bond bond, List<BondRateHistory> history,
                                    BigDecimal auctionThreshold, BigDecimal cpiFixedThreshold) {
        String isin = bond.getIsinCode();
        if (isin == null) {
            log.warn("Null ISIN on bond {}, defaulting to FIXED_COUPON", bond.getSeriesCode());
            return BondType.FIXED_COUPON;
        }
        if (isin.startsWith(DISCOUNTED_PREFIX)) {
            return BondType.DISCOUNTED;
        }

        boolean sukuk = isin.startsWith(SUKUK_PREFIX);
        BigDecimal baseIndex = bond.getBaseIndex();
        if (baseIndex != null && baseIndex.compareTo(HIGH_BASE_INDEX) > 0) {
            return sukuk ? BondType.SUKUK_CPI : BondType.FLOATING_CPI;
        }

        BigDecimal couponRate = bond.getCouponRate();
        if (couponRate == null || couponRate.compareTo(BigDecimal.ZERO) == 0) {
            return BondType.DISCOUNTED;
        }
        if (couponRate.compareTo(cpiFixedThreshold) < 0) {
            BondType type = sukuk ? BondType.SUKUK_CPI : BondType.FLOATING_CPI;
            log.debug("Bond {} classified as {} (rate {} < cpiFixedThreshold {})",
                    isin, type, couponRate, cpiFixedThreshold);
            return type;
        }

        boolean rateChanges = BondRateAnalyzer.hasRateChanges(history);
        BondType type = classifyByRateBehavior(sukuk, rateChanges, couponRate, auctionThreshold);
        log.debug("Bond {} classified as {} (sukuk={}, rateChanges={}, rate={}, historySize={})",
                isin, type, sukuk, rateChanges, couponRate, history != null ? history.size() : 0);
        return type;
    }

    private static BondType classifyByRateBehavior(boolean sukuk, boolean rateChanges,
                                                    BigDecimal couponRate, BigDecimal auctionThreshold) {
        if (sukuk) {
            return rateChanges ? BondType.SUKUK_CPI : BondType.SUKUK_FIXED;
        }
        if (rateChanges) {
            return couponRate.compareTo(auctionThreshold) >= 0
                    ? BondType.FLOATING_AUCTION
                    : BondType.FLOATING_TLREF;
        }
        return BondType.FIXED_COUPON;
    }
}
