package com.finance.market.forex.service;

import java.util.List;

/**
 * Resolved description of one forex currency and the EVDS serie codes to fetch for it: döviz
 * buying/selling always, plus efektif (cash) buying/selling when {@code hasEfektif}. {@code unit}
 * is the quote multiplier (e.g. per 100 units) applied when normalizing prices.
 */
public record ForexSerieMetadata(
        String currencyCode,
        String displayNameEn,
        String displayNameTr,
        int unit,
        boolean hasEfektif
) {

    private static final String DOVIZ_BUYING_SUFFIX = ".A.YTL";
    private static final String DOVIZ_SELLING_SUFFIX = ".S.YTL";
    private static final String EFEKTIF_BUYING_SUFFIX = ".A.EF.YTL";
    private static final String EFEKTIF_SELLING_SUFFIX = ".S.EF.YTL";
    private static final String CODE_PREFIX = "TP.DK.";

    public String dovizBuyingCode() {
        return CODE_PREFIX + currencyCode + DOVIZ_BUYING_SUFFIX;
    }

    public String dovizSellingCode() {
        return CODE_PREFIX + currencyCode + DOVIZ_SELLING_SUFFIX;
    }

    public String efektifBuyingCode() {
        return CODE_PREFIX + currencyCode + EFEKTIF_BUYING_SUFFIX;
    }

    public String efektifSellingCode() {
        return CODE_PREFIX + currencyCode + EFEKTIF_SELLING_SUFFIX;
    }

    /** All EVDS serie codes to fetch for this currency (4 when efektif is available, else 2). */
    public List<String> seriesCodes() {
        if (hasEfektif) {
            return List.of(dovizBuyingCode(), dovizSellingCode(), efektifBuyingCode(), efektifSellingCode());
        }
        return List.of(dovizBuyingCode(), dovizSellingCode());
    }
}
