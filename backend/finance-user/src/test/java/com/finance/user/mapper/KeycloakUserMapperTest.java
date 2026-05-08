package com.finance.user.mapper;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.event.*;
import com.finance.common.repository.*;

import com.finance.user.dto.AdminUserResponse;
import com.finance.user.dto.KeycloakUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakUserMapperTest {

    private final KeycloakUserMapper mapper = new KeycloakUserMapperImpl();

    @Test
    void should_copyAllFields_when_keycloakUserIsFullyPopulated() {
        KeycloakUser source = new KeycloakUser(
                "kc-id-1", "umut", "umut@finance.local",
                "Umut", "Ozturk", true, 1714521600000L);

        AdminUserResponse response = mapper.toResponse(source);

        assertThat(response.id()).isEqualTo("kc-id-1");
        assertThat(response.username()).isEqualTo("umut");
        assertThat(response.email()).isEqualTo("umut@finance.local");
        assertThat(response.firstName()).isEqualTo("Umut");
        assertThat(response.lastName()).isEqualTo("Ozturk");
        assertThat(response.enabled()).isTrue();
        assertThat(response.createdAt()).isEqualTo(Instant.ofEpochMilli(1714521600000L));
    }

    @Test
    void should_returnFalseEnabled_when_keycloakReportedNull() {
        KeycloakUser source = new KeycloakUser("id", "u", "e", null, null, null, 0L);

        AdminUserResponse response = mapper.toResponse(source);

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void should_returnFalseEnabled_when_keycloakReportedFalse() {
        KeycloakUser source = new KeycloakUser("id", "u", "e", null, null, false, 0L);

        AdminUserResponse response = mapper.toResponse(source);

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void should_returnNullCreatedAt_when_timestampIsNull() {
        KeycloakUser source = new KeycloakUser("id", "u", "e", null, null, true, null);

        AdminUserResponse response = mapper.toResponse(source);

        assertThat(response.createdAt()).isNull();
    }
}
