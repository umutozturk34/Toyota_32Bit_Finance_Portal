package com.finance.common.exception;

import lombok.Getter;

@Getter
public class TaskAlreadyRunningException extends RuntimeException {

    private final String taskType;

    public TaskAlreadyRunningException(String taskType) {
        super(taskType + " is already running, please wait");
        this.taskType = taskType;
    }
}
