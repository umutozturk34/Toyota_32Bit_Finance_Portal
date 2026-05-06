package com.finance.notification.core.dispatch;

import java.util.Optional;

public interface UserEmailLookup {

    Optional<String> findEmail(String userSub);
}
