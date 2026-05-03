package com.finance.backend.mapper;

import com.finance.backend.dto.internal.TrackedAssetUpsertCommand;
import com.finance.backend.dto.request.UpsertTrackedAssetRequest;
import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.model.TrackedAsset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TrackedAssetMapper {

    TrackedAssetResponse toResponse(TrackedAsset asset);

    TrackedAssetUpsertCommand toUpsertCommand(UpsertTrackedAssetRequest request);
}
