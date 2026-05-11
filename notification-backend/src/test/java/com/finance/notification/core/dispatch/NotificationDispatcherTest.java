package com.finance.notification.core.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.model.MarketType;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.core.dispatch.email.UserEmailLookup;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.user.UserPreferenceCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private UserPreferenceCacheService userPreferenceCacheService;
    @Mock private UserEmailLookup userEmailLookup;
    @Mock private UserStatusPort userStatus;
    @Mock private NotificationPersister persister;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationDispatcher dispatcher;

    private final NotificationHandler systemHandler = new NotificationHandler() {
        @Override public NotificationType type() { return NotificationType.SYSTEM; }

        @Override public RenderedNotification render(NotificationRequest request, Locale locale) {
            return new RenderedNotification("Title", "Body", "Subject", "system", Map.of("k", "v"));
        }
    };

    private static SystemPayload systemPayload() {
        return new SystemPayload("title", "body", null);
    }

    private static PriceAlertPayload priceAlertPayload() {
        return new PriceAlertPayload(1L, MarketType.CRYPTO, "btc", AlertDirection.ABOVE,
                BigDecimal.ONE, BigDecimal.TEN, null, null);
    }

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(
                preferenceRepository, userPreferenceCacheService, userEmailLookup,
                userStatus, objectMapper, persister, List.of(systemHandler));
        lenient().when(userStatus.isActive(anyString())).thenReturn(true);
        lenient().when(userPreferenceCacheService.resolveLocale(anyString())).thenReturn(Locale.ENGLISH);
    }

    @Test
    void dispatch_dropsWhenHandlerMissing() {
        dispatcher.dispatch(NotificationRequest.of("u", priceAlertPayload()));

        verify(persister, never()).persistBatch(any());
    }

    @Test
    void dispatch_persistsInAppWhenEnabled() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(true);
        prefs.setEmailSystem(false);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        ArgumentCaptor<List<Prepared>> captor = ArgumentCaptor.forClass(List.class);
        verify(persister).persistBatch(captor.capture());
        List<Prepared> batch = captor.getValue();
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).inappEntity()).isNotNull();
        assertThat(batch.get(0).inappEntity().getTitle()).isEqualTo("Title");
        assertThat(batch.get(0).outboxRow()).isNull();
    }

    @Test
    void dispatch_skipsInAppWhenDisabled() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveTheme("u")).thenReturn("DARK");
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.of("user@x.com"));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        ArgumentCaptor<List<Prepared>> captor = ArgumentCaptor.forClass(List.class);
        verify(persister).persistBatch(captor.capture());
        List<Prepared> batch = captor.getValue();
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).inappEntity()).isNull();
        assertThat(batch.get(0).outboxRow()).isNotNull();
    }

    @Test
    void dispatch_persistsEmailOutboxWithThemeAndLocale() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveTheme("u")).thenReturn("LIGHT");
        when(userPreferenceCacheService.resolveLocale("u")).thenReturn(Locale.ENGLISH);
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.of("user@x.com"));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        ArgumentCaptor<List<Prepared>> captor = ArgumentCaptor.forClass(List.class);
        verify(persister).persistBatch(captor.capture());
        EmailOutbox row = captor.getValue().get(0).outboxRow();
        assertThat(row.getTheme()).isEqualTo("LIGHT");
        assertThat(row.getLocale()).isEqualTo("en");
        assertThat(row.getRecipientEmail()).isEqualTo("user@x.com");
        assertThat(row.getSubject()).isEqualTo("Subject");
        assertThat(row.getTemplateName()).isEqualTo("system");
    }

    @Test
    void dispatch_skipsEmailWhenMasterSwitchOff() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setEmailEnabled(false);
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        verify(persister, never()).persistBatch(any());
        verify(userEmailLookup, never()).findEmail(anyString());
    }

    @Test
    void dispatch_skipsEmailWhenLookupEmpty() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.empty());

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        verify(persister, never()).persistBatch(any());
    }

    @Test
    void dispatch_usesDefaultPreferencesWhenAbsent() {
        when(preferenceRepository.findById("u")).thenReturn(Optional.empty());

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        verify(persister).persistBatch(any());
    }
}
