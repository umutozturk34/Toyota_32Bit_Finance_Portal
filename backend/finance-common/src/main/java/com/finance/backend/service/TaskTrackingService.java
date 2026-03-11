package com.finance.backend.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Log4j2
@Service
public class TaskTrackingService {

    private static final int MAX_HISTORY = 50;

    public record TaskInfo(String type, String status, String message,
                           Instant startedAt, Instant completedAt, String error) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type);
            m.put("status", status);
            m.put("message", message);
            m.put("startedAt", startedAt.toString());
            if (completedAt != null) {
                m.put("completedAt", completedAt.toString());
                m.put("durationMs", Duration.between(startedAt, completedAt).toMillis());
            } else {
                m.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());
            }
            if (error != null) m.put("error", error);
            return m;
        }
    }

    private final Map<String, TaskInfo> runningTasks = new ConcurrentHashMap<>();
    private final Deque<TaskInfo> taskHistory = new ConcurrentLinkedDeque<>();

    public boolean isRunning(String taskType) {
        return runningTasks.containsKey(taskType);
    }

    public TaskInfo startTask(String taskType, String message) {
        TaskInfo info = new TaskInfo(taskType, "RUNNING", message, Instant.now(), null, null);
        runningTasks.put(taskType, info);
        log.info("Task started: {}", taskType);
        return info;
    }

    public void completeTask(String taskType, TaskInfo started) {
        TaskInfo completed = new TaskInfo(taskType, "COMPLETED", started.message(),
                started.startedAt(), Instant.now(), null);
        addToHistory(completed);
        runningTasks.remove(taskType);
        log.info("Task completed: {}", taskType);
    }

    public void failTask(String taskType, TaskInfo started, String errorMsg) {
        TaskInfo failed = new TaskInfo(taskType, "FAILED", started.message(),
                started.startedAt(), Instant.now(), errorMsg);
        addToHistory(failed);
        runningTasks.remove(taskType);
        log.error("Task failed: {}", taskType);
    }

    public Map<String, Object> getStatus() {
        List<Map<String, Object>> running = runningTasks.values().stream()
                .map(TaskInfo::toMap)
                .toList();
        List<Map<String, Object>> history = taskHistory.stream()
                .map(TaskInfo::toMap)
                .toList();
        return Map.of(
                "running", running,
                "history", history,
                "runningCount", running.size());
    }

    private void addToHistory(TaskInfo info) {
        taskHistory.addFirst(info);
        while (taskHistory.size() > MAX_HISTORY) {
            taskHistory.removeLast();
        }
    }
}
