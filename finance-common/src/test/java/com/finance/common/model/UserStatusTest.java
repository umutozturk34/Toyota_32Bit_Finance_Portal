package com.finance.common.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @Test
    void shouldSetUpdatedAt_whenLifecycleHookInvoked() throws Exception {
        UserStatus status = UserStatus.builder()
                .userSub("sub-1")
                .enabled(true)
                .build();

        Method touch = UserStatus.class.getDeclaredMethod("touch");
        touch.setAccessible(true);
        touch.invoke(status);

        assertThat(status.getUpdatedAt()).isNotNull();
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getUserSub()).isEqualTo("sub-1");
    }
}
