package com.finance.app.controller.admin;



import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.common.model.MarketType;
import com.finance.app.service.AdminTaskService;
import com.finance.shared.service.TaskTrackingService;
import com.finance.shared.util.EnumParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Admin-only API to manually trigger background data refreshes (per-market snapshot/candles/full, bonds,
 * news, macro) and to stream live task status over SSE. Trigger endpoints return 202 Accepted immediately
 * while the work runs asynchronously.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    private final AdminTaskService adminTaskService;
    private final TaskTrackingService taskTrackingService;
    private final Translator translator;

    @PostMapping("/trigger/{type}/snapshot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerSnapshot(@PathVariable String type) {
        MarketType marketType = parseMarketType(type);
        return ApiResponse.success(translator.translate("api.admin.snapshotTriggered", capitalize(type)), adminTaskService.triggerSnapshot(marketType));
    }

    @PostMapping("/trigger/{type}/candles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerCandles(@PathVariable String type) {
        MarketType marketType = parseMarketType(type);
        return ApiResponse.success(translator.translate("api.admin.candlesTriggered", capitalize(type)), adminTaskService.triggerCandles(marketType));
    }

    @PostMapping("/trigger/{type}/full")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerFull(@PathVariable String type) {
        MarketType marketType = parseMarketType(type);
        return ApiResponse.success(translator.translate("api.admin.fullUpdateTriggered", capitalize(type)), adminTaskService.triggerFull(marketType));
    }

    @PostMapping("/trigger/bond/update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerBondUpdate() {
        return ApiResponse.success(translator.translate("api.admin.bondUpdateTriggered"), adminTaskService.triggerBondUpdate());
    }

    @PostMapping("/trigger/news/update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerNewsUpdate() {
        return ApiResponse.success(translator.translate("api.admin.newsUpdateTriggered"), adminTaskService.triggerNewsUpdate());
    }

    @PostMapping("/trigger/macro/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerMacroRefresh() {
        return ApiResponse.success(translator.translate("api.admin.macroRefreshTriggered"), adminTaskService.triggerMacroRefresh());
    }

    @GetMapping(path = "/tasks/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskStatus() {
        return taskTrackingService.subscribeToStatus();
    }

    @GetMapping("/tasks/status")
    public ApiResponse<com.finance.shared.dto.response.TaskStatusResponse> getTaskStatus() {
        return ApiResponse.success(translator.translate("api.admin.taskStatusFetched"), taskTrackingService.getTypedStatus());
    }

    private MarketType parseMarketType(String raw) {
        return EnumParser.parseOrBadRequest(MarketType.class, raw == null ? null : raw.toUpperCase(), "enum.field.marketType");
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }
}
