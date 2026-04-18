package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.exception.TaskAlreadyRunningException;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTrackingServiceTest {

    private TaskTrackingService service;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        service = new TaskTrackingService(appProperties);
    }

    @Test
    void startTaskRegistersAsRunning() {
        TaskInfo info = service.startTask("CRYPTO_SNAPSHOT", "Fetching crypto data");

        assertThat(service.isRunning("CRYPTO_SNAPSHOT")).isTrue();
        assertThat(info.type()).isEqualTo("CRYPTO_SNAPSHOT");
        assertThat(info.status()).isEqualTo("RUNNING");
        assertThat(info.message()).isEqualTo("Fetching crypto data");
        assertThat(info.startedAt()).isNotNull();
        assertThat(info.completedAt()).isNull();
        assertThat(info.error()).isNull();
    }

    @Test
    void startDuplicateTaskThrowsTaskAlreadyRunning() {
        service.startTask("STOCK_CANDLE", "First run");

        assertThatThrownBy(() -> service.startTask("STOCK_CANDLE", "Second run"))
                .isInstanceOf(TaskAlreadyRunningException.class)
                .satisfies(ex -> assertThat(((TaskAlreadyRunningException) ex).getTaskType())
                        .isEqualTo("STOCK_CANDLE"));
    }

    @Test
    void completeTaskRemovesFromRunningAndAddsToHistory() {
        TaskInfo started = service.startTask("FOREX_SNAPSHOT", "Updating forex");

        service.completeTask("FOREX_SNAPSHOT", started);

        assertThat(service.isRunning("FOREX_SNAPSHOT")).isFalse();
        TaskStatusResponse status = service.getTypedStatus();
        assertThat(status.running()).isEmpty();
        assertThat(status.history()).hasSize(1);
        assertThat(status.history().get(0).status()).isEqualTo("COMPLETED");
        assertThat(status.history().get(0).completedAt()).isNotNull();
        assertThat(status.history().get(0).error()).isNull();
    }

    @Test
    void failTaskRemovesFromRunningAndRecordsError() {
        TaskInfo started = service.startTask("BOND_SNAPSHOT", "Fetching bonds");

        service.failTask("BOND_SNAPSHOT", started, "API timeout");

        assertThat(service.isRunning("BOND_SNAPSHOT")).isFalse();
        TaskStatusResponse status = service.getTypedStatus();
        assertThat(status.history()).hasSize(1);
        assertThat(status.history().get(0).status()).isEqualTo("FAILED");
        assertThat(status.history().get(0).error()).isEqualTo("API timeout");
    }

    @Test
    void isRunningReturnsFalseForUnknownTask() {
        assertThat(service.isRunning("NON_EXISTENT")).isFalse();
    }

    @Test
    void historyIsCappedAt50() {
        for (int i = 0; i < 55; i++) {
            TaskInfo started = service.startTask("TASK_" + i, "msg");
            service.completeTask("TASK_" + i, started);
        }

        TaskStatusResponse status = service.getTypedStatus();
        assertThat(status.history()).hasSize(appProperties.getTask().getMaxHistory());
    }

    @Test
    void getTypedStatusReportsRunningCount() {
        service.startTask("TASK_A", "Running A");
        service.startTask("TASK_B", "Running B");

        TaskStatusResponse status = service.getTypedStatus();

        assertThat(status.runningCount()).isEqualTo(2);
        assertThat(status.running()).hasSize(2);
    }

    @Test
    void historyOrderIsMostRecentFirst() {
        TaskInfo first = service.startTask("FIRST", "1");
        service.completeTask("FIRST", first);
        TaskInfo second = service.startTask("SECOND", "2");
        service.completeTask("SECOND", second);

        TaskStatusResponse status = service.getTypedStatus();

        assertThat(status.history().get(0).type()).isEqualTo("SECOND");
        assertThat(status.history().get(1).type()).isEqualTo("FIRST");
    }

    @Test
    void completedTaskCanBeRestartedAfterCompletion() {
        TaskInfo started = service.startTask("REUSABLE", "First run");
        service.completeTask("REUSABLE", started);

        TaskInfo restarted = service.startTask("REUSABLE", "Second run");

        assertThat(service.isRunning("REUSABLE")).isTrue();
        assertThat(restarted.message()).isEqualTo("Second run");
    }

    @Test
    void durationIsCalculatedForCompletedTasks() {
        TaskInfo started = service.startTask("TIMED", "Timing test");
        service.completeTask("TIMED", started);

        TaskStatusResponse status = service.getTypedStatus();
        assertThat(status.history().get(0).durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void concurrentStartTaskAllowsExactlyOneWinner() throws InterruptedException {
        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        service.startTask("CONCURRENT_TASK", "contested");
                        successes.incrementAndGet();
                    } catch (TaskAlreadyRunningException e) {
                        failures.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threadCount - 1);
        assertThat(service.isRunning("CONCURRENT_TASK")).isTrue();
    }

    @Test
    void runTrackedRunsTaskAndCompletesIt() {
        AtomicInteger ran = new AtomicInteger();

        service.runTracked("RUN_TRACKED_OK", "happy path", ran::incrementAndGet);

        assertThat(ran.get()).isEqualTo(1);
        assertThat(service.isRunning("RUN_TRACKED_OK")).isFalse();
        TaskStatusResponse status = service.getTypedStatus();
        assertThat(status.history()).hasSize(1);
        assertThat(status.history().get(0).status()).isEqualTo("COMPLETED");
        assertThat(status.history().get(0).error()).isNull();
    }

    @Test
    void runTrackedMarksTaskFailedWhenTaskThrows() {
        service.runTracked("RUN_TRACKED_FAIL", "boom path", () -> {
            throw new IllegalStateException("boom");
        });

        assertThat(service.isRunning("RUN_TRACKED_FAIL")).isFalse();
        TaskStatusResponse status = service.getTypedStatus();
        assertThat(status.history()).hasSize(1);
        assertThat(status.history().get(0).status()).isEqualTo("FAILED");
        assertThat(status.history().get(0).error()).isEqualTo("boom");
    }

    @Test
    void runTrackedReleasesLockSoTaskCanBeRunAgain() {
        service.runTracked("REENTRANT", "first", () -> {});
        service.runTracked("REENTRANT", "second", () -> {});

        assertThat(service.isRunning("REENTRANT")).isFalse();
        TaskStatusResponse status = service.getTypedStatus();
        assertThat(status.history()).hasSize(2);
    }
}
