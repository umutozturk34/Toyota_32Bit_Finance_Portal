package com.finance.market.core.dto.response;

public record TaskTriggerResponse(String status, String message, String type) {

    public static TaskTriggerResponse started(String type, String message) {
        return new TaskTriggerResponse("started", message, type);
    }
}
