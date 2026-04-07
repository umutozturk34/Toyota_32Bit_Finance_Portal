package com.finance.backend.service;

import com.finance.backend.dto.response.TaskInfoResponse;
import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.exception.TaskAlreadyRunningException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class TaskTrackingService {

    private static final int MAX_HISTORY = 50;

    public record TaskInfo(String type, String status, String message,
                           Instant startedAt, Instant completedAt, String error) {
    }

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
        while (taskHistory.size() > MAX_HISTORY) {
            taskHistory.removeLast();
        }
    }
}
