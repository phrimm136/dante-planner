package org.danteplanner.backend.auth.token;

import java.util.Date;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Null-boundary behavior of {@link TokenBlacklistService}, over mocked Redis templates so the
 * contract is observable as the commands issued rather than as Redis state.
 */
class TokenBlacklistServiceTest {

    /**
     * Leading character of an entry written with immediate effect; the grace-period mode spells
     * it {@code 0}, and the two are indistinguishable in Redis apart from this marker.
     */
    private static final String IMMEDIATE_MARKER = "1:";

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
    void invalidateUserTokens_WhenUserIdNull_ThrowsAndStampsNothing() {
        assertThatThrownBy(() -> blacklistService.invalidateUserTokens(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId");

        verifyNoInteractions(writeTemplate);
    }

    @Test
    void clearUserInvalidation_WhenUserIdNull_ThrowsAndDeletesNothing() {
        assertThatThrownBy(() -> blacklistService.clearUserInvalidation(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId");

        verifyNoInteractions(writeTemplate);
    }

    @Test
    void revokeLogoutSession_WhenOnlyATokenRevocation_WritesOneImmediateEntryAndNoFamily() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        blacklistService.revokeLogoutSession(
                List.of(new LogoutRevocation.TokenRevocation("access.jwt", expiry)));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(writeTemplate).execute(any(RedisScript.class), keys.capture(), args.capture());

        assertThat(keys.getValue()).hasSize(1);
        assertThat(keys.getValue().get(0)).startsWith("bl:");

        Object[] argv = args.getValue();
        assertThat((String) argv[0]).startsWith(IMMEDIATE_MARKER);
        assertThat(argv[1]).isEqualTo("1");
    }

    @Test
    void revokeLogoutSession_WhenAFamilyRevocationIsIncluded_AppendsTheFamilyKeyAfterTheTokenKeys() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        blacklistService.revokeLogoutSession(List.of(
                new LogoutRevocation.TokenRevocation("refresh.jwt", expiry),
                new LogoutRevocation.FamilyRevocation("fam-1")));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(writeTemplate).execute(any(RedisScript.class), keys.capture(), args.capture());

        assertThat(keys.getValue()).hasSize(2);
        assertThat(keys.getValue().get(0)).startsWith("bl:");
        assertThat(keys.getValue().get(1)).isEqualTo(RefreshRotationService.familyKey("fam-1"));
        assertThat(args.getValue()[1]).isEqualTo("1");
    }

    @Test
    void revokeLogoutSession_WhenNoRevocations_IssuesNoRedisCommand() {
        blacklistService.revokeLogoutSession(List.of());

        verifyNoInteractions(writeTemplate);
    }

    @Test
    void revokeLogoutSession_WhenTheOnlyTokenHasLapsed_IssuesNoRedisCommand() {
        Date lapsed = new Date(System.currentTimeMillis() - 60_000);

        blacklistService.revokeLogoutSession(
                List.of(new LogoutRevocation.TokenRevocation("stale.jwt", lapsed)));

        verifyNoInteractions(writeTemplate);
    }

    @Test
    void revocationItems_WhenConstructedWithNull_Throw() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        assertThatThrownBy(() -> new LogoutRevocation.TokenRevocation(null, expiry))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("token");
        assertThatThrownBy(() -> new LogoutRevocation.TokenRevocation("access.jwt", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expiry");
        assertThatThrownBy(() -> new LogoutRevocation.FamilyRevocation(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("familyId");
    }
}
