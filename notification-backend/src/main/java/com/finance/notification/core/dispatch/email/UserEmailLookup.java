package com.finance.notification.core.dispatch.email;

import java.util.Optional;

public interface UserEmailLookup {

    Optional<String> findEmail(String userSub);
}
