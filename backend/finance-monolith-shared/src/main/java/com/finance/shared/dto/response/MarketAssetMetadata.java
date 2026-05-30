package com.finance.shared.dto.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * Sealed polymorphic carrier for per-market-type asset metadata in API responses. Jackson selects
 * the concrete subtype by deducing it from the JSON field set, so each permitted record must remain
 * structurally distinct. Shared across modules that expose enriched asset details.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(StockMetadata.class),
        @JsonSubTypes.Type(CryptoMetadata.class),
        @JsonSubTypes.Type(FundMetadata.class),
        @JsonSubTypes.Type(ForexMetadata.class),
        @JsonSubTypes.Type(CommodityMetadata.class),
        @JsonSubTypes.Type(ViopMetadata.class)
})
public sealed interface MarketAssetMetadata
        permits StockMetadata, CryptoMetadata, FundMetadata, ForexMetadata, CommodityMetadata, ViopMetadata {
}
