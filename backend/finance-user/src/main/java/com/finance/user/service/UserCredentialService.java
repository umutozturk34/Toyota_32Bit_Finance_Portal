package com.finance.user.service;
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
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private static final long EMAIL_LINK_LIFESPAN_SECONDS = 600L;
    private static final String FRONTEND_CLIENT_ID = "finance-frontend";

    private final KeycloakAdminClient client;

    public void initiatePasswordChange(String userSub, String redirectUri) {
        client.sendActionsEmail(userSub, List.of("UPDATE_PASSWORD"), FRONTEND_CLIENT_ID, redirectUri, EMAIL_LINK_LIFESPAN_SECONDS);
    }
}
