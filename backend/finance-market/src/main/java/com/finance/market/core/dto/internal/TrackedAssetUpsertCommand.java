package com.finance.market.core.dto.internal;

import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import lombok.Builder;
import lombok.Value;

/** Internal command carrying the fields needed to create or update a tracked asset. */
@Value
@Builder
public class TrackedAssetUpsertCommand {
    TrackedAssetType assetType;
    String assetCode;
    String displayName;
    String binanceSymbol;
    StockSegment stockSegment;
    Boolean indexAsset;
    Boolean compareOnly;
    Integer sortOrder;
}
