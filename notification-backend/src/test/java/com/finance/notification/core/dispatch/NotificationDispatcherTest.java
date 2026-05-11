package com.finance.notification.core.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.model.MarketType;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.email.UserEmailLookup;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.user.UserPreferenceCacheService;
import com.finance.notification.user.UserPreferenceCacheService.UserPreferenceSnapshot;
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
        NotificationDispatchProperties dispatchProperties = new NotificationDispatchProperties(
                null, null, new NotificationDispatchProperties.Fanout(200));
        dispatcher = new NotificationDispatcher(
                preferenceRepository, userPreferenceCacheService, userEmailLookup,
                userStatus, objectMapper, persister, dispatchProperties, List.of(systemHandler));
        lenient().when(userStatus.isActive(anyString())).thenReturn(true);
        lenient().when(userPreferenceCacheService.resolveLocale(anyString())).thenReturn(Locale.ENGLISH);
        lenient().when(userPreferenceCacheService.loadAll(any())).thenAnswer(inv -> {
            java.util.Map<String, UserPreferenceSnapshot> map = new java.util.HashMap<>();
            for (Object sub : (java.util.Collection<?>) inv.getArgument(0)) {
                map.put((String) sub, UserPreferenceSnapshot.defaults());
            }
            return map;
        });
        lenient().when(userStatus.activeStatusOf(any())).thenAnswer(inv -> {
            java.util.Map<String, Boolean> map = new java.util.HashMap<>();
            for (Object sub : (java.util.Collection<?>) inv.getArgument(0)) {
                map.put((String) sub, true);
            }
            return map;
        });
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

    @Test
    void should_returnZeroResult_when_dispatchBatchedReceivesEmptyList() {
        NotificationDispatcher.BatchResult result = dispatcher.dispatchBatched(List.of());

        assertThat(result.dispatched()).isZero();
        assertThat(result.failed()).isZero();
        verify(persister, never()).persistBatch(any());
    }

    @Test
    void should_persistOneBatchPerPage_when_requestsExceedPageSize() {
        NotificationDispatcher smallPage = newDispatcherWithPageSize(2);
        NotificationPreference prefs = enabledSystemPrefs("u1", "u2", "u3", "u4", "u5");
        when(preferenceRepository.findAllById(any())).thenReturn(List.of(prefs));

        List<NotificationRequest> requests = List.of(
                NotificationRequest.of("u1", systemPayload()),
                NotificationRequest.of("u2", systemPayload()),
                NotificationRequest.of("u3", systemPayload()),
                NotificationRequest.of("u4", systemPayload()),
                NotificationRequest.of("u5", systemPayload()));

        NotificationDispatcher.BatchResult result = smallPage.dispatchBatched(requests);

        assertThat(result.dispatched()).isEqualTo(5);
        verify(persister, org.mockito.Mockito.times(3)).persistBatch(any());
        verify(userStatus, org.mockito.Mockito.times(3)).activeStatusOf(any());
    }

    @Test
    void should_skipBannedRecipients_when_dispatchBatchedRunsChunk() {
        when(userStatus.activeStatusOf(any())).thenReturn(Map.of("banned", false, "active", true));
        when(preferenceRepository.findAllById(any())).thenReturn(List.of(
                enabledSystemPrefs("active")));

        NotificationDispatcher.BatchResult result = dispatcher.dispatchBatched(List.of(
                NotificationRequest.of("active", systemPayload()),
                NotificationRequest.of("banned", systemPayload())));

        assertThat(result.dispatched()).isEqualTo(1);
        ArgumentCaptor<List<Prepared>> captor = ArgumentCaptor.forClass(List.class);
        verify(persister).persistBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).userSub()).isEqualTo("active");
    }

    @Test
    void should_fallbackToDefaultPreferences_when_subAbsentFromBulkLoad() {
        when(preferenceRepository.findAllById(any())).thenReturn(List.of());

        NotificationDispatcher.BatchResult result = dispatcher.dispatchBatched(List.of(
                NotificationRequest.of("u", systemPayload())));

        assertThat(result.dispatched()).isEqualTo(1);
        verify(persister).persistBatch(any());
    }

    private NotificationDispatcher newDispatcherWithPageSize(int pageSize) {
        NotificationDispatchProperties props = new NotificationDispatchProperties(
                null, null, new NotificationDispatchProperties.Fanout(pageSize));
        return new NotificationDispatcher(
                preferenceRepository, userPreferenceCacheService, userEmailLookup,
                userStatus, objectMapper, persister, props, List.of(systemHandler));
    }

    private NotificationPreference enabledSystemPrefs(String... subs) {
        NotificationPreference p = NotificationPreference.defaultsFor(subs[0]);
        p.setInappSystem(true);
        p.setEmailSystem(false);
        return p;
    }
}
