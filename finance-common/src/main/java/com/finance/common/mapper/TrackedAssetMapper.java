package com.finance.common.mapper;

import com.finance.common.dto.internal.TrackedAssetUpsertCommand;
import com.finance.common.dto.request.UpsertTrackedAssetRequest;
import com.finance.common.dto.response.TrackedAssetResponse;
import com.finance.common.model.TrackedAsset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TrackedAssetMapper {

    TrackedAssetResponse toResponse(TrackedAsset asset);

    TrackedAssetUpsertCommand toUpsertCommand(UpsertTrackedAssetRequest request);
}
