package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.response.TaskInfoResponse;
import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.exception.TaskAlreadyRunningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Log4j2
@Service
@RequiredArgsConstructor
public class TaskTrackingService {

    public record TaskInfo(String type, String status, String message,
                           Instant startedAt, Instant completedAt, String error) {
    }

    private final AppProperties appProperties;
    private final Map<String, TaskInfo> runningTasks = new ConcurrentHashMap<>();
    private final Deque<TaskInfo> taskHistory = new ConcurrentLinkedDeque<>();

    public boolean isRunning(String taskType) {
        return runningTasks.containsKey(taskType);
    }

    public TaskInfo startTask(String taskType, String message) {
        TaskInfo info = new TaskInfo(taskType, "RUNNING", message, Instant.now(), null, null);
        TaskInfo existing = runningTasks.putIfAbsent(taskType, info);
        if (existing != null) {
            throw new TaskAlreadyRunningException(taskType);
        }
        return info;
    }

    public void completeTask(String taskType, TaskInfo started) {
        TaskInfo completed = new TaskInfo(taskType, "COMPLETED", started.message(),
                started.startedAt(), Instant.now(), null);
        addToHistory(completed);
        runningTasks.remove(taskType);
    }

    public void failTask(String taskType, TaskInfo started, String errorMsg) {
        TaskInfo failed = new TaskInfo(taskType, "FAILED", started.message(),
                started.startedAt(), Instant.now(), errorMsg);
        addToHistory(failed);
        runningTasks.remove(taskType);
    }

    public void runTracked(String taskType, String description, Runnable task) {
        TaskInfo started = startTask(taskType, description);
        try {
            task.run();
            completeTask(taskType, started);
        } catch (Exception e) {
            failTask(taskType, started, e.getMessage());
            log.error("{} failed", taskType, e);
        }
    }

    public TaskStatusResponse getTypedStatus() {
        List<TaskInfoResponse> running = runningTasks.values().stream()
                .map(this::toInfoResponse)
                .toList();
        List<TaskInfoResponse> history = taskHistory.stream()
                .map(this::toInfoResponse)
                .toList();
        return new TaskStatusResponse(running, history, running.size());
    }

    private TaskInfoResponse toInfoResponse(TaskInfo info) {
        long durationMs;
        String completedAt;
        if (info.completedAt() != null) {
            durationMs = Duration.between(info.startedAt(), info.completedAt()).toMillis();
            completedAt = info.completedAt().toString();
        } else {
            durationMs = Duration.between(info.startedAt(), Instant.now()).toMillis();
            completedAt = null;
        }
        return new TaskInfoResponse(
                info.type(), info.status(), info.message(),
                info.startedAt().toString(), completedAt, durationMs, info.error());
    }

    private void addToHistory(TaskInfo info) {
        taskHistory.addFirst(info);
        while (taskHistory.size() > appProperties.getTask().getMaxHistory()) {
            taskHistory.removeLast();
        }
    }
}
