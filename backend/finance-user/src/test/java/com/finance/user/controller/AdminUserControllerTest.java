package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.AdminUserResponse;
import com.finance.user.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private static final String ADMIN = "admin-uuid";

    @Mock private AdminUserService service;
    @Mock private Translator translator;

    private AdminUserController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(service, translator);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject(ADMIN).build();
    }

    @Test
    void listUsers_delegatesToService() {
        List<AdminUserResponse> users = List.of();
        when(service.listUsers(0, 50, "ali")).thenReturn(users);
        when(translator.translate("api.admin.usersRetrieved")).thenReturn("listed");

        ApiResponse<List<AdminUserResponse>> response = controller.listUsers(0, 50, "ali");

        assertThat(response.getMessage()).isEqualTo("listed");
        assertThat(response.getData()).isSameAs(users);
    }

    @Test
    void countUsers_delegatesToService() {
        when(service.countUsers(null)).thenReturn(7L);
        when(translator.translate("api.admin.userCountRetrieved")).thenReturn("count");

        ApiResponse<Long> response = controller.countUsers(null);

        assertThat(response.getMessage()).isEqualTo("count");
        assertThat(response.getData()).isEqualTo(7L);
    }

    @Test
    void banUser_invokesServiceWithCallerSubject() {
        when(translator.translate("api.admin.userBanned")).thenReturn("banned");

        ApiResponse<Void> response = controller.banUser(jwt, "target-uuid");

        assertThat(response.getMessage()).isEqualTo("banned");
        verify(service).banUser("target-uuid", ADMIN);
    }

    @Test
    void unbanUser_invokesServiceWithTargetId() {
        when(translator.translate("api.admin.userUnbanned")).thenReturn("unbanned");

        ApiResponse<Void> response = controller.unbanUser("target-uuid");

        assertThat(response.getMessage()).isEqualTo("unbanned");
        verify(service).unbanUser("target-uuid");
    }
}
