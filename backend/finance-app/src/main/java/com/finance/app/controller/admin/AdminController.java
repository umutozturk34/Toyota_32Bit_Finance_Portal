package com.finance.app.controller.admin;
import com.finance.market.bond.model.Bond;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.common.dto.ApiResponse;
import com.finance.market.core.dto.response.TaskTriggerResponse;
import com.finance.common.model.MarketType;
import com.finance.app.service.AdminTaskService;
import com.finance.common.service.TaskTrackingService;
import com.finance.common.util.EnumParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminTaskService adminTaskService;
    private final TaskTrackingService taskTrackingService;

    @PostMapping("/trigger/{type}/snapshot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerSnapshot(@PathVariable String type) {
        MarketType marketType = parseMarketType(type);
        String label = capitalize(type) + " snapshot triggered";
        return ApiResponse.success(label, adminTaskService.triggerSnapshot(marketType));
    }

    @PostMapping("/trigger/{type}/candles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerCandles(@PathVariable String type) {
        MarketType marketType = parseMarketType(type);
        String label = capitalize(type) + " candles triggered";
        return ApiResponse.success(label, adminTaskService.triggerCandles(marketType));
    }

    @PostMapping("/trigger/{type}/full")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerFull(@PathVariable String type) {
        MarketType marketType = parseMarketType(type);
        String label = capitalize(type) + " full update triggered";
        return ApiResponse.success(label, adminTaskService.triggerFull(marketType));
    }

    @PostMapping("/trigger/bond/update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerBondUpdate() {
        return ApiResponse.success("Bond update triggered", adminTaskService.triggerBondUpdate());
    }

    @PostMapping("/trigger/news/update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TaskTriggerResponse> triggerNewsUpdate() {
        return ApiResponse.success("News update triggered", adminTaskService.triggerNewsUpdate());
    }

    @GetMapping(path = "/tasks/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskStatus() {
        return taskTrackingService.subscribeToStatus();
    }

    private MarketType parseMarketType(String raw) {
        return EnumParser.parseOrBadRequest(MarketType.class, raw == null ? null : raw.toUpperCase(), "market type");
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }
}
