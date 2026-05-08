package com.finance.portfolio.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.util.List;

public record BackfillStatusResponse(
        boolean running,
        Long since,
        List<PendingAsset> pendingAssets
) {
    public record PendingAsset(String assetType, String assetCode) {}
}
