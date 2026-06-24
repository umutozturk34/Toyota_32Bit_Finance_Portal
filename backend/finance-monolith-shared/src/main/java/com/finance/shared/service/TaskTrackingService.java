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

/**
 * In-memory registry tracking long-running tasks (schedulers, manual triggers) across the monolith
 * and streaming their state to subscribers over SSE. At most one task per type may run concurrently;
 * starting a duplicate type throws. State and history live only in this process and are lost on restart.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TaskTrackingService {

    /** Immutable snapshot of a tracked task's lifecycle; {@code completedAt}/{@code error} are null while running. */
    public record TaskInfo(String type, String status, String message,
                           Instant startedAt, Instant completedAt, String error) {
    }

    private final AppProperties appProperties;
    private final Map<String, TaskInfo> runningTasks = new ConcurrentHashMap<>();
    private final Deque<TaskInfo> taskHistory = new ConcurrentLinkedDeque<>();
    private final CopyOnWriteArrayList<SseEmitter> statusEmitters = new CopyOnWriteArrayList<>();

    /** Whether a task of the given type is currently registered as running. */
    public boolean isRunning(String taskType) {
        return runningTasks.containsKey(taskType);
    }

    /**
     * Registers a task as running and broadcasts the change.
     *
     * @throws com.finance.common.exception.TaskAlreadyRunningException if a task of this type is already running
     */
    public TaskInfo startTask(String taskType, String message) {
        TaskInfo info = new TaskInfo(taskType, "RUNNING", message, Instant.now(), null, null);
        TaskInfo existing = runningTasks.putIfAbsent(taskType, info);
        if (existing != null) {
            throw new TaskAlreadyRunningException(taskType);
        }
        broadcastStatus();
        return info;
    }

    /**
     * Removes the running entry and records a COMPLETED history item, preserving the original start
     * time so duration can be derived; broadcasts the change to SSE subscribers.
     *
     * @param started the TaskInfo returned by {@link #startTask}
     */
    public void completeTask(String taskType, TaskInfo started) {
        finishTask(taskType, new TaskInfo(taskType, "COMPLETED", started.message(),
                started.startedAt(), Instant.now(), null));
    }

    /**
     * Removes the running entry and records a FAILED history item carrying the error message,
     * preserving the original start time; broadcasts the change to SSE subscribers.
     *
     * @param started  the TaskInfo returned by {@link #startTask}
     * @param errorMsg human-readable failure cause stored on the history entry
     */
    public void failTask(String taskType, TaskInfo started, String errorMsg) {
        finishTask(taskType, new TaskInfo(taskType, "FAILED", started.message(),
                started.startedAt(), Instant.now(), errorMsg));
    }

    /**
     * Records the task as ended in the distinct {@code API_KEY_MISSING} state — a configuration problem (a
     * required upstream API key is absent), not a transient runtime failure — so the UI can show an actionable
     * "API key missing" status instead of a generic failure that hides the real, fixable cause.
     *
     * @param started  the TaskInfo returned by {@link #startTask}
     * @param errorMsg human-readable description of which key is missing
     */
    public void failApiKeyMissing(String taskType, TaskInfo started, String errorMsg) {
        finishTask(taskType, new TaskInfo(taskType, "API_KEY_MISSING", started.message(),
                started.startedAt(), Instant.now(), errorMsg));
    }

    private void finishTask(String taskType, TaskInfo finalState) {
        runningTasks.remove(taskType);
        taskHistory.removeIf(t -> t.type().equals(taskType));
        addToHistory(finalState);
        broadcastStatus();
    }

    /** Registers a non-timing-out SSE subscriber, immediately pushing the current status snapshot. */
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

    /**
     * Runs {@code task} synchronously under start/complete/fail tracking; exceptions are caught,
     * recorded as a failed task, and logged rather than propagated.
     */
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

    /**
     * Builds the current status snapshot: the running tasks, the bounded recent history, and the
     * running count. Durations are computed live for still-running tasks (now − startedAt).
     */
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

    /** Prepends to history, evicting oldest entries beyond the configured max-history bound. */
    private void addToHistory(TaskInfo info) {
        taskHistory.addFirst(info);
        while (taskHistory.size() > appProperties.getTask().getMaxHistory()) {
            taskHistory.removeLast();
        }
    }
}
