package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.dto.response.TaskTriggerResponse;
import com.finance.backend.service.AdminTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminTaskService adminTaskService;

    @PostMapping("/trigger/crypto/snapshot")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerCryptoSnapshot() {
        return ResponseEntity.accepted().body(ApiResponse.success("Crypto snapshot triggered", adminTaskService.triggerCryptoSnapshot()));
    }

    @PostMapping("/trigger/crypto/candles")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerCryptoCandles() {
        return ResponseEntity.accepted().body(ApiResponse.success("Crypto candles triggered", adminTaskService.triggerCryptoCandles()));
    }

    @PostMapping("/trigger/crypto/full")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerCryptoFull() {
        return ResponseEntity.accepted().body(ApiResponse.success("Crypto full update triggered", adminTaskService.triggerCryptoFull()));
    }

    @PostMapping("/trigger/stock/snapshot")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerStockSnapshot() {
        return ResponseEntity.accepted().body(ApiResponse.success("Stock snapshot triggered", adminTaskService.triggerStockSnapshot()));
    }

    @PostMapping("/trigger/stock/candles")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerStockCandles() {
        return ResponseEntity.accepted().body(ApiResponse.success("Stock candles triggered", adminTaskService.triggerStockCandles()));
    }

    @PostMapping("/trigger/stock/full")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerStockFull() {
        return ResponseEntity.accepted().body(ApiResponse.success("Stock full update triggered", adminTaskService.triggerStockFull()));
    }

    @PostMapping("/trigger/forex/snapshot")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerForexSnapshot() {
        return ResponseEntity.accepted().body(ApiResponse.success("Forex snapshot triggered", adminTaskService.triggerForexSnapshot()));
    }

    @PostMapping("/trigger/forex/candles")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerForexCandles() {
        return ResponseEntity.accepted().body(ApiResponse.success("Forex candles triggered", adminTaskService.triggerForexCandles()));
    }

    @PostMapping("/trigger/forex/full")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerForexFull() {
        return ResponseEntity.accepted().body(ApiResponse.success("Forex full update triggered", adminTaskService.triggerForexFull()));
    }

    @PostMapping("/trigger/fund/snapshot")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerFundSnapshot() {
        return ResponseEntity.accepted().body(ApiResponse.success("Fund snapshot triggered", adminTaskService.triggerFundSnapshot()));
    }

    @PostMapping("/trigger/fund/candles")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerFundCandles() {
        return ResponseEntity.accepted().body(ApiResponse.success("Fund candles triggered", adminTaskService.triggerFundCandles()));
    }

    @PostMapping("/trigger/fund/full")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerFundFull() {
        return ResponseEntity.accepted().body(ApiResponse.success("Fund full update triggered", adminTaskService.triggerFundFull()));
    }

    @PostMapping("/trigger/bond/update")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerBondUpdate() {
        return ResponseEntity.accepted().body(ApiResponse.success("Bond update triggered", adminTaskService.triggerBondUpdate()));
    }

    @PostMapping("/trigger/news/update")
    public ResponseEntity<ApiResponse<TaskTriggerResponse>> triggerNewsUpdate() {
        return ResponseEntity.accepted().body(ApiResponse.success("News update triggered", adminTaskService.triggerNewsUpdate()));
    }

    @GetMapping("/tasks/status")
    public ResponseEntity<ApiResponse<TaskStatusResponse>> getTaskStatus() {
        return ResponseEntity.ok(ApiResponse.success("Task status retrieved", adminTaskService.getTaskStatus()));
    }
}
