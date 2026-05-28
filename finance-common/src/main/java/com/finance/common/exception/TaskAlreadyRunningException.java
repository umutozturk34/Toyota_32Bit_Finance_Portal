package com.finance.common.exception;

import lombok.Getter;

/**
 * Raised when a single-flight background task is triggered while an instance of the same
 * {@code taskType} is already running; mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
@Getter
public class TaskAlreadyRunningException extends RuntimeException {

    private final String taskType;

    public TaskAlreadyRunningException(String taskType) {
        super("error.task.alreadyRunning");
        this.taskType = taskType;
    }

    public Object[] getMessageArgs() {
        return new Object[] { taskType };
    }
}
