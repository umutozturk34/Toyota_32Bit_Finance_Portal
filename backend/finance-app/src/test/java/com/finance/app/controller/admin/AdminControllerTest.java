package com.finance.app.controller.admin;

import com.finance.app.service.AdminTaskService;
import com.finance.common.dto.ApiResponse;
import com.finance.common.exception.BadRequestException;
import com.finance.common.i18n.Translator;
import com.finance.common.model.MarketType;
import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminControllerTest {

    @Mock private AdminTaskService adminTaskService;
    @Mock private TaskTrackingService taskTrackingService;
    @Mock private Translator translator;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(adminTaskService, taskTrackingService, translator);
        when(translator.translate(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void triggerSnapshot_parsesMarketType_andDelegates() {
        TaskTriggerResponse expected = TaskTriggerResponse.started("STOCK", "ok");
        when(adminTaskService.triggerSnapshot(MarketType.STOCK)).thenReturn(expected);

        ApiResponse<TaskTriggerResponse> response = controller.triggerSnapshot("stock");

        assertThat(response.getData()).isEqualTo(expected);
        verify(adminTaskService).triggerSnapshot(MarketType.STOCK);
    }

    @Test
    void triggerCandles_parsesMarketType_andDelegates() {
        TaskTriggerResponse expected = TaskTriggerResponse.started("CRYPTO", "ok");
        when(adminTaskService.triggerCandles(MarketType.CRYPTO)).thenReturn(expected);

        ApiResponse<TaskTriggerResponse> response = controller.triggerCandles("crypto");

        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void triggerFull_parsesMarketType_andDelegates() {
        TaskTriggerResponse expected = TaskTriggerResponse.started("FOREX", "ok");
        when(adminTaskService.triggerFull(MarketType.FOREX)).thenReturn(expected);

        ApiResponse<TaskTriggerResponse> response = controller.triggerFull("forex");

        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void triggerSnapshot_throwsBadRequest_whenTypeUnknown() {
        assertThatThrownBy(() -> controller.triggerSnapshot("UNKNOWN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void triggerBondUpdate_delegatesWithoutTypeArg() {
        TaskTriggerResponse expected = TaskTriggerResponse.started("BOND", "ok");
        when(adminTaskService.triggerBondUpdate()).thenReturn(expected);

        ApiResponse<TaskTriggerResponse> response = controller.triggerBondUpdate();

        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void triggerNewsUpdate_delegatesWithoutTypeArg() {
        TaskTriggerResponse expected = TaskTriggerResponse.started("NEWS", "ok");
        when(adminTaskService.triggerNewsUpdate()).thenReturn(expected);

        ApiResponse<TaskTriggerResponse> response = controller.triggerNewsUpdate();

        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void streamTaskStatus_delegatesToTrackingService() {
        SseEmitter emitter = new SseEmitter();
        when(taskTrackingService.subscribeToStatus()).thenReturn(emitter);

        SseEmitter result = controller.streamTaskStatus();

        assertThat(result).isSameAs(emitter);
    }
}
