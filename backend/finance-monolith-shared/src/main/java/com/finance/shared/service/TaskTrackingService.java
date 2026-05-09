package com.finance.shared.service;

import com.finance.common.config.AppProperties;
import com.finance.shared.dto.response.TaskInfoResponse;
import com.finance.shared.dto.response.TaskStatusResponse;
import com.finance.common.exception.TaskAlreadyRunningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final CopyOnWriteArrayList<SseEmitter> statusEmitters = new CopyOnWriteArrayList<>();

    public boolean isRunning(String taskType) {
        return runningTasks.containsKey(taskType);
    }

    public TaskInfo startTask(String taskType, String message) {
        TaskInfo info = new TaskInfo(taskType, "RUNNING", message, Instant.now(), null, null);
        TaskInfo existing = runningTasks.putIfAbsent(taskType, info);
        if (existing != null) {
            throw new TaskAlreadyRunningException(taskType);
        }
        broadcastStatus();
        return info;
    }

    public void completeTask(String taskType, TaskInfo started) {
        finishTask(taskType, new TaskInfo(taskType, "COMPLETED", started.message(),
                started.startedAt(), Instant.now(), null));
    }

    public void failTask(String taskType, TaskInfo started, String errorMsg) {
        finishTask(taskType, new TaskInfo(taskType, "FAILED", started.message(),
                started.startedAt(), Instant.now(), errorMsg));
    }

    private void finishTask(String taskType, TaskInfo finalState) {
        runningTasks.remove(taskType);
        taskHistory.removeIf(t -> t.type().equals(taskType));
        addToHistory(finalState);
        broadcastStatus();
    }

    public SseEmitter subscribeToStatus() {
        SseEmitter emitter = new SseEmitter(0L);
        statusEmitters.add(emitter);
        emitter.onCompletion(() -> statusEmitters.remove(emitter));
        emitter.onTimeout(() -> statusEmitters.remove(emitter));
        emitter.onError((e) -> statusEmitters.remove(emitter));
        sendStatus(emitter, getTypedStatus());
        return emitter;
    }

    private void broadcastStatus() {
        if (statusEmitters.isEmpty()) return;
        TaskStatusResponse snapshot;
        try {
            snapshot = getTypedStatus();
        } catch (Throwable t) {
            log.warn("Failed to compute task status snapshot", t);
            return;
        }
        for (SseEmitter emitter : statusEmitters) {
            sendStatus(emitter, snapshot);
        }
    }

    private static void sendStatus(SseEmitter emitter, TaskStatusResponse snapshot) {
        try {
            emitter.send(SseEmitter.event().name("task-status").data(snapshot));
        } catch (Throwable t) {
            try { emitter.completeWithError(t); } catch (Throwable ignored) { }
        }
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
