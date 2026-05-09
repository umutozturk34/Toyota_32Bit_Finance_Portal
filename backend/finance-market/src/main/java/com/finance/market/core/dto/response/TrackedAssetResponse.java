package com.finance.market.core.dto.response;

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
    StockSegment stockSegment;
    boolean indexAsset;
    boolean compareOnly;
    Integer sortOrder;
}
