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

@Service
@Log4j2
public class TrackedAssetRefreshService {

    private final Map<TrackedAssetType, TrackedAssetDataService> dataServices;

    public TrackedAssetRefreshService(List<TrackedAssetDataService> services) {
        this.dataServices = new EnumMap<>(TrackedAssetType.class);
        services.forEach(s -> this.dataServices.put(s.getAssetType(), s));
    }

    public void validateAssetExists(TrackedAssetUpsertCommand command) {
        resolve(command.getAssetType()).validateExists(command);
    }

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
