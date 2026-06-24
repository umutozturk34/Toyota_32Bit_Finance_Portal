package com.finance.shared.dto.response;

import com.finance.common.model.StockSegment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Stock-specific {@link MarketAssetMetadata}: market segment, exchange, volume and intraday OHLC, plus the
 * company künye and weighted index membership shown on the detail page. The künye/membership fields are
 * populated only on the single-asset detail response (enriched from İş Yatırım); list responses leave them
 * null/empty to avoid loading reference data for every row.
 */
public record StockMetadata(
        StockSegment stockSegment,
        Long volume,
        String exchange,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        String sector,
        LocalDate foundedDate,
        String city,
        List<IndexMembership> indexMemberships,
        List<IndexConstituent> constituents
) implements MarketAssetMetadata {

    /** A BIST index the stock belongs to, with the stock's weight in that index. */
    public record IndexMembership(String indexCode, BigDecimal weight) {
    }

    /**
     * A member stock of an index (the reverse view, present only on an index's detail), with its weight and
     * full company name. {@code stockName} is null when the member stock has not been enriched with a name,
     * so the client falls back to the bare symbol for the hover tooltip.
     */
    public record IndexConstituent(String stockSymbol, BigDecimal weight, String stockName) {
    }
}
