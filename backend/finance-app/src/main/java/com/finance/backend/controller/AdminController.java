package com.finance.backend.controller;

import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.dto.response.TaskTriggerResponse;
import com.finance.backend.service.AdminTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminTaskService adminTaskService;

    @PostMapping("/trigger/crypto/snapshot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerCryptoSnapshot() {
        return adminTaskService.triggerCryptoSnapshot();
    }

    @PostMapping("/trigger/crypto/candles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerCryptoCandles() {
        return adminTaskService.triggerCryptoCandles();
    }

    @PostMapping("/trigger/crypto/full")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerCryptoFull() {
        return adminTaskService.triggerCryptoFull();
    }

    @PostMapping("/trigger/stock/snapshot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerStockSnapshot() {
        return adminTaskService.triggerStockSnapshot();
    }

    @PostMapping("/trigger/stock/candles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerStockCandles() {
        return adminTaskService.triggerStockCandles();
    }

    @PostMapping("/trigger/stock/full")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerStockFull() {
        return adminTaskService.triggerStockFull();
    }

    @PostMapping("/trigger/forex/snapshot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerForexSnapshot() {
        return adminTaskService.triggerForexSnapshot();
    }

    @PostMapping("/trigger/forex/candles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerForexCandles() {
        return adminTaskService.triggerForexCandles();
    }

    @PostMapping("/trigger/forex/full")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerForexFull() {
        return adminTaskService.triggerForexFull();
    }

    @PostMapping("/trigger/fund/snapshot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerFundSnapshot() {
        return adminTaskService.triggerFundSnapshot();
    }

    @PostMapping("/trigger/fund/candles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerFundCandles() {
        return adminTaskService.triggerFundCandles();
    }

    @PostMapping("/trigger/fund/full")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskTriggerResponse triggerFundFull() {
        return adminTaskService.triggerFundFull();
    }

    @GetMapping("/tasks/status")
    public TaskStatusResponse getTaskStatus() {
        return adminTaskService.getTaskStatus();
    }
}