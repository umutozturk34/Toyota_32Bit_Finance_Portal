package com.finance.common.exception;

/**
 * Thrown when a manually-triggered task cannot be accepted because every worker thread is busy. Maps to
 * HTTP 429 so the caller is told the system is busy and to retry shortly, rather than getting a misleading
 * "started" acknowledgement for work that never ran.
 */
public class TaskCapacityExceededException extends RuntimeException {

    public TaskCapacityExceededException(String message) {
        super(message);
    }
}
