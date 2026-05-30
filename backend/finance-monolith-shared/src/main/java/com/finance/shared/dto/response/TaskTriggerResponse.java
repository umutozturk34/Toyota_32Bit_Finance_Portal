package com.finance.shared.dto.response;

/** Acknowledgement returned when a task is manually triggered, echoing its status, type, and message. */
public record TaskTriggerResponse(String status, String message, String type) {

    /** Builds an acknowledgement with the {@code "started"} status. */
    public static TaskTriggerResponse started(String type, String message) {
        return new TaskTriggerResponse("started", message, type);
    }
}
