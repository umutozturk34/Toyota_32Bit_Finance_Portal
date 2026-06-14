package com.finance.market.bond.util;


import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.List;

/**
 * Classifies a bond into a {@link BondType} from its ISIN prefix (TRB=discounted, TRD=sukuk), coupon
 * rate, market price and rate-change history. CPI/inflation linkage is inferred from the coupon, never
 * from the market price of a coupon-bearing bond: a nominal bond whose coupon exceeds the market yield
 * legitimately trades above par, so a price-only heuristic wrongly flagged such bonds as CPI-linked and
 * hid their yield. A bond is treated as inflation-indexed when its coupon is a low real coupon, when a
 * reference-index value has leaked into the coupon field (a value far above any real semi-annual rate),
 * or — only for a zero-coupon bond — when its price is inflated well above par. Rate changes split
 * auction vs. TLREF floating.
 */
@Log4j2
public final class BondTypeResolver {

    /** Above-par price that, for a ZERO-coupon bond only, signals index-uplifted (CPI) rather than a discount bill. */
    private static final BigDecimal HIGH_BASE_INDEX = new BigDecimal("110");
    /** A coupon larger than this is the CPI reference index leaking into the .ORAN field, not a real coupon. */
    private static final BigDecimal COUPON_INDEX_CEILING = new BigDecimal("100");
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
        BigDecimal couponRate = bond.getCouponRate();

        // A coupon far larger than any real semi-annual rate is the CPI reference index leaking into the
        // .ORAN field (observed in the thousands for inflation-indexed bonds) — the bond is CPI-linked.
        if (couponRate != null && couponRate.compareTo(COUPON_INDEX_CEILING) > 0) {
            return sukuk ? BondType.SUKUK_CPI : BondType.FLOATING_CPI;
        }

        // No coupon: a price at or below par is a genuine zero-coupon discount instrument, whereas a price
        // inflated well above par with no coupon is a zero-real-coupon CPI bond (its value carries the index
        // uplift). Price is only trusted to mean "indexed" here, where there is no coupon to misinterpret.
        if (couponRate == null || couponRate.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal price = bond.getBaseIndex();
            if (price != null && price.compareTo(HIGH_BASE_INDEX) > 0) {
                return sukuk ? BondType.SUKUK_CPI : BondType.FLOATING_CPI;
            }
            return BondType.DISCOUNTED;
        }

        // A low real coupon is the hallmark of a CPI-linked bond (inflation compensation accrues to
        // principal, leaving only a small real coupon).
        if (couponRate.compareTo(cpiFixedThreshold) < 0) {
            BondType type = sukuk ? BondType.SUKUK_CPI : BondType.FLOATING_CPI;
            log.debug("Bond {} classified as {} (real coupon {} < cpiFixedThreshold {})",
                    isin, type, couponRate, cpiFixedThreshold);
            return type;
        }

        // A normal coupon means a nominal bond; its market price must NOT reclassify it (above-par nominal
        // bonds are normal). Fixed vs floating is decided purely by whether the coupon resets over time.
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
