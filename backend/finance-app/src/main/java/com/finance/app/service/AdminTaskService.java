package com.finance.app.service;

import com.finance.common.model.MarketType;
import com.finance.shared.dto.response.TaskTriggerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Entry point for admin-triggered data refreshes (market snapshot/candles/full, bonds, news, macro). Each
 * call kicks off the work asynchronously via {@link AdminTaskRunner} and returns immediately with a
 * started-task response; progress is observed through task tracking.
 */
@Service
@RequiredArgsConstructor
public class AdminTaskService {

    private final TaskRefreshRegistry registry;
    private final AdminTaskRunner runner;

    public TaskTriggerResponse triggerSnapshot(MarketType type) {
        return triggerMarketRefresh(type, "snapshot", " snapshot update started in background");
    }

    public TaskTriggerResponse triggerCandles(MarketType type) {
        return triggerMarketRefresh(type, "candles", " candle update started in background");
    }

    public TaskTriggerResponse triggerFull(MarketType type) {
        return triggerMarketRefresh(type, "full", " full market update started in background");
    }

    public TaskTriggerResponse triggerBondUpdate() {
        return runner.execute("bond-update",
                "Bond update started in background",
                registry::runBondUpdate);
    }

    public TaskTriggerResponse triggerNewsUpdate() {
        return runner.execute("news-update",
                "News feed update started in background",
                registry::runNewsUpdate);
    }

    public TaskTriggerResponse triggerMacroRefresh() {
        return runner.execute("macro-refresh",
                "Macro indicator refresh started in background",
                registry::runMacroRefresh);
    }

    private TaskTriggerResponse triggerMarketRefresh(MarketType type, String suffix, String messageTail) {
        String taskType = type.name().toLowerCase() + "-" + suffix;
        String message = type.name() + messageTail;
        return runner.execute(taskType, message, () -> registry.runMarketRefresh(type));
    }
}
