package org.danteplanner.backend.controller;

import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.dto.user.UserDeletionResponse;
import org.danteplanner.backend.service.UserAccountLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

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
    private RateLimitConfig rateLimitConfig;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserController userController;

    private static final int GRACE_PERIOD_DAYS = 30;
    private static final Long TEST_USER_ID = 123L;

    @BeforeEach
    void setUp() {
        // Set the grace period field using reflection since it's @Value injected
        ReflectionTestUtils.setField(userController, "gracePeriodDays", GRACE_PERIOD_DAYS);
    }

    @Nested
    @DisplayName("deleteMyAccount Tests")
    class DeleteMyAccountTests {

        @Test
        @DisplayName("Should return success response with deletion details")
        void deleteMyAccount_returnsSuccessResponse() {
            // Arrange
            Instant scheduledDeleteAt = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS));
            when(authentication.getPrincipal()).thenReturn(TEST_USER_ID);
            when(lifecycleService.deleteAccount(TEST_USER_ID)).thenReturn(scheduledDeleteAt);

            // Act
            ResponseEntity<UserDeletionResponse> response = userController.deleteMyAccount(authentication);

            // Assert
            assertNotNull(response);
            assertEquals(200, response.getStatusCode().value());

            UserDeletionResponse body = response.getBody();
            assertNotNull(body);
            assertEquals("Account scheduled for deletion", body.message());
            assertNotNull(body.deletedAt());
            assertEquals(scheduledDeleteAt, body.permanentDeleteAt());
            assertEquals(GRACE_PERIOD_DAYS, body.gracePeriodDays());

            verify(lifecycleService).deleteAccount(TEST_USER_ID);
        }

        @Test
        @DisplayName("Should be idempotent and return existing scheduled date on repeat call")
        void deleteMyAccount_idempotent_returnsExistingScheduledDate() {
            // Arrange - simulate second call returning same scheduled date
            Instant originalScheduledAt = Instant.now().plus(Duration.ofDays(25));
            when(authentication.getPrincipal()).thenReturn(TEST_USER_ID);
            when(lifecycleService.deleteAccount(TEST_USER_ID)).thenReturn(originalScheduledAt);

            // Act
            ResponseEntity<UserDeletionResponse> response = userController.deleteMyAccount(authentication);

            // Assert
            assertNotNull(response);
            assertEquals(200, response.getStatusCode().value());
            assertEquals(originalScheduledAt, response.getBody().permanentDeleteAt());
        }

        @Test
        @DisplayName("Should extract user ID from authentication principal")
        void deleteMyAccount_extractsUserIdFromPrincipal() {
            // Arrange
            Long userId = 456L;
            Instant scheduledAt = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS));
            when(authentication.getPrincipal()).thenReturn(userId);
            when(lifecycleService.deleteAccount(userId)).thenReturn(scheduledAt);

            // Act
            userController.deleteMyAccount(authentication);

            // Assert
            verify(authentication).getPrincipal();
            verify(lifecycleService).deleteAccount(userId);
        }

        @Test
        @DisplayName("Should use configured grace period in response")
        void deleteMyAccount_usesConfiguredGracePeriod() {
            // Arrange
            int customGracePeriod = 60;
            ReflectionTestUtils.setField(userController, "gracePeriodDays", customGracePeriod);

            when(authentication.getPrincipal()).thenReturn(TEST_USER_ID);
            when(lifecycleService.deleteAccount(TEST_USER_ID))
                    .thenReturn(Instant.now().plus(Duration.ofDays(customGracePeriod)));

            // Act
            ResponseEntity<UserDeletionResponse> response = userController.deleteMyAccount(authentication);

            // Assert
            assertEquals(customGracePeriod, response.getBody().gracePeriodDays());
        }
    }
}
