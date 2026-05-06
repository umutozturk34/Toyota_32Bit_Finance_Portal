package com.finance.notification.broadcast.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RecipientDirectory {

    Page<String> findUserSubs(Pageable pageable);

    long count();
}
