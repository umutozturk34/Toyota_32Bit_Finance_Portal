package com.finance.backend.dto.response;

import com.finance.backend.model.StockSegment;
import com.finance.backend.model.TrackedAssetType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrackedAssetResponse {
    TrackedAssetType assetType;
    String assetCode;
    String displayName;
    String binanceSymbol;
    boolean enabled;
    StockSegment stockSegment;
    boolean indexAsset;
    boolean compareOnly;
    Integer sortOrder;
}
