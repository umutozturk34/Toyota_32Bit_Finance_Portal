package com.finance.app.service;

import com.finance.common.exception.CriticalApiFailureException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.TaskCapacityExceededException;
import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTaskRunnerTest {

    @Mock private TaskTrackingService taskTracker;

    private Executor inlineExecutor;
    private AdminTaskRunner runner;
    private TaskTrackingService.TaskInfo info;

    @BeforeEach
    void setUp() {
        inlineExecutor = Runnable::run;
        runner = new AdminTaskRunner(taskTracker, inlineExecutor);
        info = new TaskTrackingService.TaskInfo("t", "RUNNING", "msg", Instant.now(), null, null);
    }

    @Test
    void shouldStartAndCompleteTask_whenRunnableSucceeds() {
        when(taskTracker.startTask(eq("snapshot"), eq("running"))).thenReturn(info);
        Runnable task = () -> { };

        TaskTriggerResponse response = runner.execute("snapshot", "running", task);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("started");
        assertThat(response.type()).isEqualTo("snapshot");
        assertThat(response.message()).isEqualTo("running");
        verify(taskTracker).startTask("snapshot", "running");
        verify(taskTracker).completeTask("snapshot", info);
        verify(taskTracker, never()).failTask(anyString(), eq(info), anyString());
    }

    @Test
    void shouldFailTask_whenRunnableThrows() {
        when(taskTracker.startTask(eq("snapshot"), eq("running"))).thenReturn(info);
        Runnable task = () -> { throw new IllegalStateException("boom"); };

        TaskTriggerResponse response = runner.execute("snapshot", "running", task);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("started");
        verify(taskTracker).startTask("snapshot", "running");
        verify(taskTracker).failTask("snapshot", info, "boom");
        verify(taskTracker, never()).completeTask(anyString(), eq(info));
    }

    @Test
    void shouldCompleteNotFail_whenRunnableThrowsExternalApiFailure() {
        when(taskTracker.startTask(eq("forex-full"), eq("running"))).thenReturn(info);
        Runnable task = () -> { throw new ExternalApiException("EVDS", "rate limited"); };

        runner.execute("forex-full", "running", task);

        verify(taskTracker).completeTask("forex-full", info);
        verify(taskTracker, never()).failTask(anyString(), eq(info), anyString());
    }

    @Test
    void shouldCompleteNotFail_whenRunnableThrowsCriticalApiFailure() {
        when(taskTracker.startTask(eq("macro-refresh"), eq("running"))).thenReturn(info);
        Runnable task = () -> { throw new CriticalApiFailureException("3 of 5 failed"); };

        runner.execute("macro-refresh", "running", task);

        verify(taskTracker).completeTask("macro-refresh", info);
        verify(taskTracker, never()).failTask(anyString(), eq(info), anyString());
    }

    @Test
    void shouldSkipWithoutRegistering_whenAllWorkerThreadsBusy() throws InterruptedException {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(10);
        pool.initialize();
        AdminTaskRunner poolRunner = new AdminTaskRunner(taskTracker, pool);
        when(taskTracker.startTask(eq("busy"), anyString())).thenReturn(info);
        CountDownLatch hold = new CountDownLatch(1);

        poolRunner.execute("busy", "m", () -> {
            try { hold.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        assertThatThrownBy(() -> poolRunner.execute("second", "m", () -> { }))
                .isInstanceOf(TaskCapacityExceededException.class);
        verify(taskTracker, never()).startTask(eq("second"), anyString());
        hold.countDown();
        pool.shutdown();
    }

    @Test
    void shouldRejectAndCleanUp_whenExecutorRejectsTask() {
        Executor rejecting = command -> { throw new RejectedExecutionException("pool full"); };
        AdminTaskRunner saturatedRunner = new AdminTaskRunner(taskTracker, rejecting);
        when(taskTracker.startTask(eq("forex-full"), eq("running"))).thenReturn(info);

        assertThatThrownBy(() -> saturatedRunner.execute("forex-full", "running", () -> { }))
                .isInstanceOf(TaskCapacityExceededException.class);
        verify(taskTracker).completeTask("forex-full", info);
        verify(taskTracker, never()).failTask(anyString(), eq(info), anyString());
    }

    @Test
    void shouldReturnStartedResponse_withProvidedTypeAndMessage() {
        when(taskTracker.startTask(anyString(), anyString())).thenReturn(info);

        TaskTriggerResponse response = runner.execute("news", "fetching news", () -> { });

        assertThat(response.type()).isEqualTo("news");
        assertThat(response.message()).isEqualTo("fetching news");
        assertThat(response.status()).isEqualTo("started");
    }
}
