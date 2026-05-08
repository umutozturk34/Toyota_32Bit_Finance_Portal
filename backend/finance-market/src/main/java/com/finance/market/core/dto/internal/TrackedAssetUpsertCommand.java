package com.finance.market.core.dto.internal;

import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrackedAssetUpsertCommand {
    TrackedAssetType assetType;
    String assetCode;
    String displayName;
    String binanceSymbol;
    Boolean enabled;
    StockSegment stockSegment;
    Boolean indexAsset;
    Boolean compareOnly;
    Integer sortOrder;
}
