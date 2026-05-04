package com.finance.notification.messaging.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageDuplicateGuardTest {

    private MessageDuplicateGuard guard;

    @BeforeEach
    void setUp() {
        guard = new MessageDuplicateGuard();
    }

    @Test
    void isDuplicate_returnsFalseOnFirstSubmissionPerUser() {
        boolean result = guard.isDuplicate("u-1", "hello");

        assertThat(result).isFalse();
    }

    @Test
    void isDuplicate_returnsTrueWhenSameBodyResubmittedByUser() {
        guard.isDuplicate("u-1", "hello");

        boolean result = guard.isDuplicate("u-1", "hello");

        assertThat(result).isTrue();
    }

    @Test
    void isDuplicate_returnsFalseWhenBodyDiffers() {
        guard.isDuplicate("u-1", "hello");

        boolean result = guard.isDuplicate("u-1", "world");

        assertThat(result).isFalse();
    }

    @Test
    void isDuplicate_isolatesAcrossUsers() {
        guard.isDuplicate("u-1", "hello");

        boolean result = guard.isDuplicate("u-2", "hello");

        assertThat(result).isFalse();
    }

    @Test
    void isDuplicate_onlyRemembersLastHash() {
        guard.isDuplicate("u-1", "first");
        guard.isDuplicate("u-1", "second");

        boolean firstAgain = guard.isDuplicate("u-1", "first");

        assertThat(firstAgain).isFalse();
    }
}
