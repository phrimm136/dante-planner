package org.danteplanner.backend.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Null-boundary behavior of {@link TokenBlacklistService}, over mocked Redis templates so the
 * contract is observable as the commands issued rather than as Redis state.
 */
class TokenBlacklistServiceTest {

    private StringRedisTemplate writeTemplate;
    private StringRedisTemplate readTemplate;
    private TokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        writeTemplate = mock(StringRedisTemplate.class);
        readTemplate = mock(StringRedisTemplate.class);
        blacklistService = new TokenBlacklistService(
                writeTemplate, readTemplate, new SimpleMeterRegistry(),
                TokenBlacklistService.DEFAULT_REFRESH_TOKEN_EXPIRY_MS);
    }

    @Test
    @DisplayName("guard-invoked-with-null-throws: invalidateUserTokens rejects a null user id")
    void invalidateUserTokens_WhenUserIdNull_ThrowsAndStampsNothing() {
        assertThatThrownBy(() -> blacklistService.invalidateUserTokens(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(writeTemplate);
    }

    @Test
    @DisplayName("guard-invoked-with-null-throws: clearUserInvalidation rejects a null user id")
    void clearUserInvalidation_WhenUserIdNull_ThrowsAndDeletesNothing() {
        assertThatThrownBy(() -> blacklistService.clearUserInvalidation(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(writeTemplate);
    }
}
