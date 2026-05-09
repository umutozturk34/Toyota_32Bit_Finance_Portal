package com.finance.market.core.mapper;

import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.dto.request.UpsertTrackedAssetRequest;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.common.model.TrackedAsset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TrackedAssetMapper {

    TrackedAssetResponse toResponse(TrackedAsset asset);

    TrackedAssetUpsertCommand toUpsertCommand(UpsertTrackedAssetRequest request);
}
