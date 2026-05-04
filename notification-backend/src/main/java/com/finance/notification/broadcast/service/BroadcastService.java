package com.finance.notification.broadcast.service;

import com.finance.notification.broadcast.dto.BroadcastRequest;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.user.UserPreferenceCache;
import com.finance.notification.user.UserPreferenceCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final UserPreferenceCacheRepository userCacheRepository;
    private final NotificationDispatcher dispatcher;

    @Transactional
    public int broadcast(String adminSub, BroadcastRequest request) {
        List<UserPreferenceCache> users = userCacheRepository.findAll();
        Map<String, Object> payload = Map.of(
                "title", request.title(),
                "body", request.body(),
                "issuedBy", adminSub
        );
        for (UserPreferenceCache user : users) {
            dispatcher.dispatch(NotificationRequest.of(
                    user.getUserSub(),
                    NotificationType.SYSTEM,
                    payload
            ));
        }
        log.info("Broadcast sent admin={} title={} recipients={}", adminSub, request.title(), users.size());
        return users.size();
    }
}
