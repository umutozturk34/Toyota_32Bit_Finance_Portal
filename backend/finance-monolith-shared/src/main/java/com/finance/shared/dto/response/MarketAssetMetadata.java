package com.finance.shared.dto.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(StockMetadata.class),
        @JsonSubTypes.Type(CryptoMetadata.class),
        @JsonSubTypes.Type(FundMetadata.class),
        @JsonSubTypes.Type(ForexMetadata.class),
        @JsonSubTypes.Type(CommodityMetadata.class)
})
public sealed interface MarketAssetMetadata
        permits StockMetadata, CryptoMetadata, FundMetadata, ForexMetadata, CommodityMetadata {
}
