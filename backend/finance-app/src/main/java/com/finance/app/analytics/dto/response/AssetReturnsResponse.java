package com.finance.app.analytics.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * The asset-returns dataset: every tracked spot asset (stocks, crypto, forex, funds, commodities — bonds
 * and VIOP excluded) with its TRY returns across all windows. Computed in TRY only (independent of the
 * user's display currency), cached in-app and refreshed daily after the evening market-data refresh.
 * {@code asOf} is the window end (today); an empty {@code assets} list means the data isn't ready yet
 * (cold start, before the warm-up has run).
 */
public record AssetReturnsResponse(
        LocalDate asOf,
        List<AssetReturnRow> assets) {
}
