package org.danteplanner.backend.controller;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.user.controller.UserController;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserDeletionResponse;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserSessionService;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserController.
 *
 * <p>Tests the DELETE /api/user/me endpoint logic with mocked dependencies.
 * Note: Integration tests for this endpoint require proper transaction handling
 * that is currently not stable in the test suite. The core business logic
 * is tested here and in UserServiceTest.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserAccountLifecycleService lifecycleService;

    @Mock
    private UserService userService;

    @Mock
    private UserSettingsService userSettingsService;

    @Mock
    private EpithetConfig epithetConfig;

    @Mock
    private SsePublisher ssePublisher;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private UserSessionService userSessionService;

    /**
     * Real, so the auth cookies the controller clears are inspectable state on the response.
     */
    @Spy
    private CookieUtils cookieUtils = new CookieUtils(true, "", "Lax");

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
        return new UserController(lifecycleService, userService, userSettingsService, ssePublisher,
                epithetConfig, rateLimitService, userSessionService, cookieUtils, gracePeriodDays);
    }

    @Nested
    @DisplayName("deleteMyAccount Tests")
    class DeleteMyAccountTests {

        @BeforeEach
        void putAuthCookiesOnRequest() {
            request.setCookies(
                    new Cookie(CookieConstants.ACCESS_TOKEN, TEST_ACCESS_TOKEN),
                    new Cookie(CookieConstants.REFRESH_TOKEN, TEST_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("Should return success response with deletion details")
        void deleteMyAccount_WhenAuthenticated_ReturnsSuccessResponse() {
            // Arrange
            Instant scheduledDeleteAt = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS));
            when(lifecycleService.deleteAccount(TEST_USER_ID)).thenReturn(scheduledDeleteAt);

            // Act
            ResponseEntity<UserDeletionResponse> result = userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert
            assertNotNull(result);
            assertEquals(200, result.getStatusCode().value());

            UserDeletionResponse body = result.getBody();
            assertNotNull(body);
            assertEquals("Account scheduled for deletion", body.message());
            assertNotNull(body.deletedAt());
            assertEquals(scheduledDeleteAt, body.permanentDeleteAt());
            assertEquals(GRACE_PERIOD_DAYS, body.gracePeriodDays());
        }

        @Test
        @DisplayName("Should blacklist tokens and clear cookies on deletion")
        void deleteMyAccount_WhenAuthenticated_BlacklistsTokensAndClearsCookies() {
            // Arrange
            Instant scheduledDeleteAt = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS));
            when(lifecycleService.deleteAccount(TEST_USER_ID)).thenReturn(scheduledDeleteAt);

            // Act
            userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert - both auth cookies come back expired, so the browser drops them
            Cookie clearedAccess = response.getCookie(CookieConstants.ACCESS_TOKEN);
            assertNotNull(clearedAccess);
            assertEquals(0, clearedAccess.getMaxAge());
            Cookie clearedRefresh = response.getCookie(CookieConstants.REFRESH_TOKEN);
            assertNotNull(clearedRefresh);
            assertEquals(0, clearedRefresh.getMaxAge());
            // Blacklisting lands in Redis and has no response-visible form; asserting it as state
            // needs the containerized tier with a live TokenBlacklistService. The arguments prove
            // both tokens were read from their own cookies.
            verify(userSessionService).logout(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Should be idempotent and return existing scheduled date on repeat call")
        void deleteMyAccount_WhenIdempotent_ReturnsExistingScheduledDate() {
            // Arrange - simulate second call returning same scheduled date
            Instant originalScheduledAt = Instant.now().plus(Duration.ofDays(25));
            when(lifecycleService.deleteAccount(TEST_USER_ID)).thenReturn(originalScheduledAt);

            // Act
            ResponseEntity<UserDeletionResponse> result = userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert
            assertNotNull(result);
            assertEquals(200, result.getStatusCode().value());
            assertEquals(originalScheduledAt, result.getBody().permanentDeleteAt());
        }

        @Test
        @DisplayName("Should pass the authenticated user ID to the lifecycle service")
        void deleteMyAccount_WhenAuthenticated_PassesUserIdToLifecycleService() {
            // Arrange
            Long userId = 456L;
            Instant scheduledAt = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS));
            when(lifecycleService.deleteAccount(userId)).thenReturn(scheduledAt);

            // Act
            ResponseEntity<UserDeletionResponse> result =
                    userController.deleteMyAccount(userId, request, response);

            // Assert - the schedule stubbed for this id is the one that comes back, so the id the
            // controller passed on can only have been the one it was handed
            assertEquals(scheduledAt, result.getBody().permanentDeleteAt());
        }

        @Test
        @DisplayName("Should use configured grace period in response")
        void deleteMyAccount_WhenConfiguredGracePeriod_UsesItInResponse() {
            // Arrange
            int customGracePeriod = 60;
            userController = controllerWithGracePeriod(customGracePeriod);

            when(lifecycleService.deleteAccount(TEST_USER_ID))
                    .thenReturn(Instant.now().plus(Duration.ofDays(customGracePeriod)));

            // Act
            ResponseEntity<UserDeletionResponse> result = userController.deleteMyAccount(TEST_USER_ID, request, response);

            // Assert
            assertEquals(customGracePeriod, result.getBody().gracePeriodDays());
        }
    }

    @Nested
    @DisplayName("updateSettings Tests")
    class UpdateSettingsTests {

        @Test
        @DisplayName("Publishes the cross-pod SSE settings invalidation after persisting the update")
        void sseSettingsInvalidationCrossPod_WhenSettingsUpdated_PublishesInvalidationForUser() {
            // Arrange
            UpdateUserSettingsRequest updateRequest = new UpdateUserSettingsRequest(true, null, null, null);
            UserSettingsResponse persisted = new UserSettingsResponse(true, true, true, false);
            when(userSettingsService.updateSettings(TEST_USER_ID, updateRequest)).thenReturn(persisted);

            // Act
            ResponseEntity<UserSettingsResponse> result =
                    userController.updateSettings(TEST_USER_ID, updateRequest);

            // Assert - the invalidation must go out for the updated user, after the persist,
            // so every pod drops its stale settingsCache entry. The ordering is only visible at
            // the publisher seam here; observing the drop itself needs the containerized tier
            // with a live Redis pub/sub SsePublisher and a second pod.
            assertEquals(200, result.getStatusCode().value());
            assertEquals(persisted, result.getBody());
            InOrder inOrder = inOrder(userSettingsService, ssePublisher);
            inOrder.verify(userSettingsService).updateSettings(TEST_USER_ID, updateRequest);
            inOrder.verify(ssePublisher).publishSettingsInvalidation(TEST_USER_ID);
        }
    }
}
