package com.finance.backend.dto.response;

import java.util.List;

public record BackfillStatusResponse(
        boolean running,
        Long since,
        List<PendingAsset> pendingAssets
) {
    public record PendingAsset(String assetType, String assetCode) {}
}
