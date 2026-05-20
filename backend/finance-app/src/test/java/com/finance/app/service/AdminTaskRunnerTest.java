package com.finance.app.service;

import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.Executor;

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
    void shouldReturnStartedResponse_withProvidedTypeAndMessage() {
        when(taskTracker.startTask(anyString(), anyString())).thenReturn(info);

        TaskTriggerResponse response = runner.execute("news", "fetching news", () -> { });

        assertThat(response.type()).isEqualTo("news");
        assertThat(response.message()).isEqualTo("fetching news");
        assertThat(response.status()).isEqualTo("started");
    }
}
