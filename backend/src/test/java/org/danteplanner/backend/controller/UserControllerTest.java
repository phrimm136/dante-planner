package org.danteplanner.backend.controller;
import org.danteplanner.backend.user.controller.UserController;

import jakarta.servlet.http.Cookie;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseEnvelope;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.RecordingSessionEndHandler;
import org.danteplanner.backend.support.RecordingSessionEndHandler.EndAllSessions;
import org.danteplanner.backend.support.RecordingSessionEndHandler.EndSession;
import org.danteplanner.backend.support.RecordingSseChannelSender;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserDeletionResponse;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    private final RecordingSseChannelSender sseChannel = new RecordingSseChannelSender();

    private final RecordingSessionEndHandler sessionEndHandler = new RecordingSessionEndHandler();

    /**
     * Real, so the auth cookies the controller clears are inspectable state on the response.
     */
    private final CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private UserController userController;

    private static final int GRACE_PERIOD_DAYS = 30;
    private static final Long TEST_USER_ID = 123L;
    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";

    @BeforeEach
    void setUp() {
        userController = controllerWithGracePeriod(GRACE_PERIOD_DAYS);
    }

    private UserController controllerWithGracePeriod(int gracePeriodDays) {
        return new UserController(
                new UserAccountLifecycleService(userRepository, List.of(), sessionEndHandler, gracePeriodDays),
                null,
                new UserSettingsService(userSettingsRepository, userRepository),
                new SsePublisher(sseChannel, Jackson2ObjectMapperBuilder.json().build(), new SimpleMeterRegistry()),
                null, sessionEndHandler, cookieUtils, gracePeriodDays);
    }

    private User givenAnAccount(Long userId) {
        User user = TestDataFactory.unsavedUser(userId);
        when(userRepository.findWithLockById(userId)).thenReturn(Optional.of(user));
        return user;
    }

    @Nested
    class DeleteMyAccountTests {

        @BeforeEach
        void putAuthCookiesOnRequest() {
            request.setCookies(
                    new Cookie(CookieConstants.ACCESS_TOKEN, TEST_ACCESS_TOKEN),
                    new Cookie(CookieConstants.REFRESH_TOKEN, TEST_REFRESH_TOKEN));
        }

        @Test
        void deleteMyAccount_WhenAuthenticated_ReturnsSuccessResponse() {
            // Arrange
            User account = givenAnAccount(TEST_USER_ID);

            // Act
            ResponseEntity<UserDeletionResponse> result = userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(200);

            UserDeletionResponse body = result.getBody();
            assertThat(body).isNotNull();
            assertThat(body.message()).isEqualTo("Account scheduled for deletion");
            assertThat(body.deletedAt()).isNotNull();
            assertThat(body.permanentDeleteAt()).isEqualTo(account.getPermanentDeleteScheduledAt());
            assertThat(body.gracePeriodDays()).isEqualTo(GRACE_PERIOD_DAYS);
        }

        @Test
        void deleteMyAccount_WhenAuthenticated_BlacklistsTokensAndClearsCookies() {
            // Arrange
            givenAnAccount(TEST_USER_ID);

            // Act
            userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert - both auth cookies come back expired, so the browser drops them
            Cookie clearedAccess = response.getCookie(CookieConstants.ACCESS_TOKEN);
            assertThat(clearedAccess).isNotNull();
            assertThat(clearedAccess.getMaxAge()).isEqualTo(0);
            Cookie clearedRefresh = response.getCookie(CookieConstants.REFRESH_TOKEN);
            assertThat(clearedRefresh).isNotNull();
            assertThat(clearedRefresh.getMaxAge()).isEqualTo(0);
            // Blacklisting lands in Redis and has no response-visible form; asserting it as state
            // needs the containerized tier with a live TokenBlacklistService. The arguments prove
            // both tokens were read from their own cookies.
            assertThat(sessionEndHandler.calls()).containsExactly(
                    new EndAllSessions(TEST_USER_ID),
                    new EndSession(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN));
        }

        @Test
        void deleteMyAccount_WhenIdempotent_ReturnsExistingScheduledDate() {
            // Arrange - simulate second call returning same scheduled date
            Instant originalScheduledAt = Instant.now().plus(Duration.ofDays(25));
            givenAnAccount(TEST_USER_ID).softDelete(originalScheduledAt);

            // Act
            ResponseEntity<UserDeletionResponse> result = userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody().permanentDeleteAt()).isEqualTo(originalScheduledAt);
        }

        @Test
        void deleteMyAccount_WhenAuthenticated_PassesUserIdToLifecycleService() {
            // Arrange
            Long userId = 456L;
            User account = givenAnAccount(userId);

            // Act
            ResponseEntity<UserDeletionResponse> result =
                    userController.deleteMyAccount(userId, request, response);

            // Assert
            assertThat(account.isDeleted()).isTrue();
            assertThat(result.getBody().permanentDeleteAt()).isEqualTo(account.getPermanentDeleteScheduledAt());
        }

        @Test
        void deleteMyAccount_WhenConfiguredGracePeriod_UsesItInResponse() {
            // Arrange
            int customGracePeriod = 60;
            userController = controllerWithGracePeriod(customGracePeriod);
            givenAnAccount(TEST_USER_ID);

            // Act
            ResponseEntity<UserDeletionResponse> result = userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert
            assertThat(result.getBody().gracePeriodDays()).isEqualTo(customGracePeriod);
        }
    }

    @Nested
    class UpdateSettingsTests {

        @Test
        void sseSettingsInvalidationCrossPod_WhenSettingsUpdated_PublishesInvalidationForUser() {
            // Arrange
            UpdateUserSettingsRequest updateRequest = new UpdateUserSettingsRequest(true, null, null, null);
            List<List<SseEnvelope>> invalidationsAtPersist = new ArrayList<>();
            when(userSettingsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(TestDataFactory.unsavedUser(TEST_USER_ID)));
            when(userSettingsRepository.insert(any(UserSettings.class))).thenAnswer(invocation -> {
                invalidationsAtPersist.add(sseChannel.envelopes(SseChannel.USER));
                return invocation.getArgument(0);
            });

            // Act
            ResponseEntity<UserSettingsResponse> result =
                    userController.updateSettings(TEST_USER_ID, updateRequest);

            // Assert - the invalidation must go out for the updated user, after the persist,
            // so every pod drops its stale settingsCache entry.
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo(new UserSettingsResponse(true, true, true, true, false));
            assertThat(invalidationsAtPersist).containsExactly(List.of());
            assertThat(sseChannel.envelopes(SseChannel.USER)).containsExactly(
                    new SseEnvelope(SseEventType.SETTINGS_INVALIDATED, TEST_USER_ID, null, null, null, null, null));
        }
    }
}
