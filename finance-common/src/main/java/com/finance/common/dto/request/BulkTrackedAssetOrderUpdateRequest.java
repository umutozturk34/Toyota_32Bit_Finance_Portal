package com.finance.common.dto.request;

import com.finance.common.model.TrackedAssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkTrackedAssetOrderUpdateRequest {

    @NotNull
    private TrackedAssetType assetType;

    @NotEmpty
    @Valid
    private List<TrackedAssetOrderItemRequest> items;
}
