package com.finance.common.exception;

import lombok.Getter;

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
