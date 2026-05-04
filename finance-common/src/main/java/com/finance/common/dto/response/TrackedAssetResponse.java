package com.finance.common.dto.response;

import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
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
