package com.finance.app.service;

import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.shared.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Log4j2
@Component
@RequiredArgsConstructor
class AdminTaskRunner {

    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    TaskTriggerResponse execute(String taskType, String message, Runnable task) {
        TaskTrackingService.TaskInfo info = taskTracker.startTask(taskType, message);
        log.info("Task started: {}", taskType);

        taskExecutor.execute(() -> {
            try {
                task.run();
                taskTracker.completeTask(taskType, info);
                log.info("Task completed: {}", taskType);
            } catch (Exception e) {
                taskTracker.failTask(taskType, info, e.getMessage());
                log.error("Task failed: {}", taskType, e);
            }
        });

        return TaskTriggerResponse.started(taskType, message);
    }
}
