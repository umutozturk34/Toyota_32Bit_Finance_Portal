package com.finance.backend.service;

import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.util.TrackedOrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Log4j2
@Service
@RequiredArgsConstructor
public class TrackedMarketViewService {

    private final TrackedAssetService trackedAssetService;

    public <T> List<T> getEnabledAndOrdered(TrackedAssetType type,
                                            List<T> snapshots,
                                            Function<T, String> codeExtractor) {
        List<String> orderedCodes = trackedAssetService.getEnabledCodes(type);
        Set<String> enabledCodes = new HashSet<>(orderedCodes);

        List<T> filtered = snapshots.stream()
                .filter(item -> enabledCodes.contains(codeExtractor.apply(item)))
                .toList();

        return TrackedOrderUtil.sortByTrackedCodes(filtered, orderedCodes, codeExtractor);
    }

    public <T, C> T getEnabledSnapshot(TrackedAssetType type,
                                       String rawCode,
                                       MarketCacheService<T, C> cacheService) {
        String normalizedCode = trackedAssetService.resolveEnabledCodeOrThrow(type, rawCode);
        return cacheService.getSnapshot(normalizedCode);
    }

    public <T, C> List<C> getEnabledHistory(TrackedAssetType type,
                                            String rawCode,
                                            MarketCacheService<T, C> cacheService) {
        String normalizedCode = trackedAssetService.resolveEnabledCodeOrThrow(type, rawCode);
        return cacheService.getHistory(normalizedCode);
    }
}
