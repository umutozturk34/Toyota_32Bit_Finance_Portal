package com.finance.common.cache;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface AssetSnapshotCache {

    Optional<AssetSnapshot> findByCode(MarketType type, String code);

    Map<String, AssetSnapshot> findByCodes(MarketType type, Set<String> codes);
}
