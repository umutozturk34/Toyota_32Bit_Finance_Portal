package com.finance.app.service;

import com.finance.common.exception.CriticalApiFailureException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.TaskCapacityExceededException;
import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.shared.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs an admin task on the shared task executor under task-tracking, recording start/complete/fail and
 * returning a started response immediately so the HTTP call doesn't block on the background work.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class AdminTaskRunner {

    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    private final AtomicInteger inFlight = new AtomicInteger();

    TaskTriggerResponse execute(String taskType, String message, Runnable task) {
        if (inFlight.get() >= workerCapacity()) {
            // No free worker thread: reject instead of queueing. We don't register the task (so it never shows
            // as "started" in the panel) and signal busy with a 429, so the caller sees a "system busy" hint
            // rather than a misleading started acknowledgement for work that never ran.
            log.warn("All {} worker threads busy — rejecting {}", workerCapacity(), taskType);
            throw new TaskCapacityExceededException("error.task.capacityExceeded");
        }
        inFlight.incrementAndGet();
        TaskTrackingService.TaskInfo info = taskTracker.startTask(taskType, message);
        log.info("Task started: {}", taskType);

        try {
            taskExecutor.execute(() -> {
                try {
                    task.run();
                    taskTracker.completeTask(taskType, info);
                    log.info("Task completed: {}", taskType);
                } catch (Exception e) {
                    finishWithFailure(taskType, info, e);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            // Backstop: the pool also rejected despite the capacity check (a race, or another executor user
            // filled the queue). Roll back the in-flight count and clear the task, then signal busy too.
            inFlight.decrementAndGet();
            taskTracker.completeTask(taskType, info);
            log.warn("Task executor rejected {}: {}", taskType, e.getMessage());
            throw new TaskCapacityExceededException("error.task.capacityExceeded");
        }

        return TaskTriggerResponse.started(taskType, message);
    }

    /**
     * The pool's core size — the tasks that run concurrently before the executor starts queueing. Capping
     * in-flight admin tasks here means a trigger with no free core thread is skipped rather than queued, so
     * the panel never piles up (the executor only grows past core once its queue fills, which we avoid).
     */
    private int workerCapacity() {
        return taskExecutor instanceof ThreadPoolTaskExecutor tpte ? tpte.getCorePoolSize() : Integer.MAX_VALUE;
    }

    /**
     * External-data flakiness (rate limits, or a &gt;50% batch failure tagged {@code CRITICAL_API_FAILURE}) is
     * reported as completed rather than failed: rows that succeeded already committed in their own
     * transactions, so the refresh did partial work and the admin UI shouldn't flag a red "failed" for a
     * transient upstream issue. Genuine errors still fail the task.
     */
    private void finishWithFailure(String taskType, TaskTrackingService.TaskInfo info, Exception e) {
        if (isBestEffortDataFailure(e)) {
            log.warn("Task {} completed with partial data ({}): {}",
                    taskType, e.getClass().getSimpleName(), e.getMessage());
            taskTracker.completeTask(taskType, info);
        } else {
            taskTracker.failTask(taskType, info, e.getMessage());
            log.error("Task failed: {}", taskType, e);
        }
    }

    private static boolean isBestEffortDataFailure(Exception e) {
        return e instanceof ExternalApiException || e instanceof CriticalApiFailureException;
    }
}
