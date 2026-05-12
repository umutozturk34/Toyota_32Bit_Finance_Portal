package com.finance.common.security;

import com.finance.common.model.UserStatus;
import com.finance.common.repository.UserStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbUserStatusAdapterTest {

    @Mock private UserStatusRepository repository;

    private DbUserStatusAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DbUserStatusAdapter(repository);
    }

    private UserStatus status(String sub, boolean enabled) {
        UserStatus s = new UserStatus();
        s.setUserSub(sub);
        s.setEnabled(enabled);
        return s;
    }

    @Test
    void isActive_returnsFalse_whenSubNull() {
        boolean result = adapter.isActive(null);

        assertThat(result).isFalse();
        verify(repository, never()).findById(any());
    }

    @Test
    void isActive_returnsFalse_whenSubBlank() {
        boolean result = adapter.isActive("   ");

        assertThat(result).isFalse();
    }

    @Test
    void isActive_returnsRepositoryValue_whenStatusPresent() {
        when(repository.findById("user-1")).thenReturn(Optional.of(status("user-1", false)));

        boolean result = adapter.isActive("user-1");

        assertThat(result).isFalse();
    }

    @Test
    void isActive_returnsTrue_whenRepositoryEmpty() {
        when(repository.findById("user-2")).thenReturn(Optional.empty());

        boolean result = adapter.isActive("user-2");

        assertThat(result).isTrue();
    }

    @Test
    void activeStatusOf_returnsEmpty_whenInputCollectionEmpty() {
        Map<String, Boolean> result = adapter.activeStatusOf(List.of());

        assertThat(result).isEmpty();
        verify(repository, never()).findAllById(any());
    }

    @Test
    void activeStatusOf_ignoresNullAndBlankEntries() {
        Map<String, Boolean> result = adapter.activeStatusOf(Arrays.asList(null, "   "));

        assertThat(result).isEmpty();
    }

    @Test
    void activeStatusOf_returnsRepositoryValues_andDefaultsMissingToTrue() {
        when(repository.findAllById(any())).thenReturn(List.of(status("user-1", false)));

        Map<String, Boolean> result = adapter.activeStatusOf(List.of("user-1", "user-2"));

        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("user-1", false);
        assertThat(result).containsEntry("user-2", true);
    }
}
