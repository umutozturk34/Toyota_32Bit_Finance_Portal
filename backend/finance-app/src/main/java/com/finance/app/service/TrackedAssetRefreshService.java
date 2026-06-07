package com.finance.app.service;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.service.TrackedAssetDataService;


import com.finance.common.model.TrackedAssetType;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches per-{@link TrackedAssetType} data-service operations (existence validation, async refresh,
 * async cache clear). Async work runs on the shared task executor and logs (rather than throws) on failure
 * so a background refresh can't break the triggering request.
 */
@Service
@Log4j2
public class TrackedAssetRefreshService {

    private final Map<TrackedAssetType, TrackedAssetDataService> dataServices;

    /**
     * Indexes the injected data services by their {@link TrackedAssetType} into an {@link EnumMap} for
     * O(1) per-type dispatch. Assumes one service per asset type (a later service silently overrides an
     * earlier one sharing the same type).
     */
    public TrackedAssetRefreshService(List<TrackedAssetDataService> services) {
        this.dataServices = new EnumMap<>(TrackedAssetType.class);
        services.forEach(s -> this.dataServices.put(s.getAssetType(), s));
    }

    /**
     * Validates that the asset referenced by the upsert command exists at its source, delegating to the
     * data service for the command's asset type.
     *
     * @throws IllegalArgumentException if no data service is registered for the command's asset type
     */
    public void validateAssetExists(TrackedAssetUpsertCommand command) {
        resolve(command.getAssetType()).validateExists(command);
    }

    /**
     * Asynchronously refreshes the single asset's data on the shared task executor. Failures are logged
     * and swallowed so a background refresh cannot fail the request that triggered it.
     */
    @Async("taskExecutor")
    public void refreshAsync(TrackedAssetType type, String code) {
        log.info("Async refresh started for {} / {}", type, code);
        try {
            resolve(type).refresh(code);
            log.info("Async refresh completed for {} / {}", type, code);
        } catch (Exception ex) {
            log.warn("Async refresh failed for {} / {}: {}", type, code, ex.getMessage());
        }
    }

    /**
     * Asynchronously evicts the cached data for the single asset on the shared task executor. Failures are
     * logged and swallowed so a background eviction cannot fail the triggering request.
     */
    @Async("taskExecutor")
    public void clearCacheAsync(TrackedAssetType type, String code) {
        try {
            resolve(type).clearCache(code);
        } catch (Exception ex) {
            log.warn("Async cache clear failed for {} / {}: {}", type, code, ex.getMessage());
        }
    }

    private TrackedAssetDataService resolve(TrackedAssetType type) {
        TrackedAssetDataService service = dataServices.get(type);
        if (service == null) {
            throw new IllegalArgumentException("error.trackedAsset.noDataService");
        }
        return service;
    }
}
