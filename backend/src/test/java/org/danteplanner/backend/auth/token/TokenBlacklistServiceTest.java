package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.support.AuthReads;
import java.util.Date;
import java.util.List;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RoleOutages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Null-boundary behavior of {@link TokenBlacklistService}, over mocked Redis templates so the
 * contract is observable as the commands issued rather than as Redis state.
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    /**
     * Leading character of an entry written with immediate effect; the grace-period mode spells
     * it {@code 0}, and the two are indistinguishable in Redis apart from this marker.
     */
    private static final String IMMEDIATE_MARKER = "1:";

    @Mock
    private StringRedisTemplate writeTemplate;

    @Mock
    private StringRedisTemplate readTemplate;

    @Mock
    private StringRedisTemplate openAuthTemplate;

    @Mock
    private ValueOperations<String, String> readValues;

    private TokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new TokenBlacklistService(
                AuthReads.writesTo(writeTemplate), AuthReads.from(readTemplate), new SimpleMeterRegistry(),
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
    void revokeLogoutSession_WhenTheAuthRoleIsOpen_FailsAtOnceWithAConnectionFailure() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        assertThatThrownBy(() -> serviceWithTheAuthRoleOpen().revokeLogoutSession(List.of(
                new LogoutRevocation.TokenRevocation("access.jwt", expiry),
                new LogoutRevocation.FamilyRevocation("fam-1"))))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining("breaker open");
        verifyNoInteractions(openAuthTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokeLogoutSession_WhenTheRevocationTimesOut_FailsWithAConnectionFailure() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        when(writeTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("Redis command timed out"));

        assertThatThrownBy(() -> blacklistService.revokeLogoutSession(List.of(
                new LogoutRevocation.TokenRevocation("access.jwt", expiry),
                new LogoutRevocation.FamilyRevocation("fam-1"))))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining("did not answer in time");
    }

    @Test
    void blacklistToken_WhenTheAuthRoleIsOpen_FailsAtOnceWithAConnectionFailure() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        assertThatThrownBy(() -> serviceWithTheAuthRoleOpen().blacklistToken("access.jwt", expiry))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining("breaker open");
        verifyNoInteractions(openAuthTemplate);
    }

    @Test
    void invalidateUserTokens_WhenTheAuthRoleIsOpen_FailsAtOnceWithAConnectionFailure() {
        assertThatThrownBy(() -> serviceWithTheAuthRoleOpen().invalidateUserTokens(1L))
                .isExactlyInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining("breaker open");
        verifyNoInteractions(openAuthTemplate);
    }

    @Test
    void preloadLogoutRevokeScript_WhenTheAuthRoleIsOpen_DoesNotThrow() {
        assertThatCode(() -> serviceWithTheAuthRoleOpen().preloadLogoutRevokeScript()).doesNotThrowAnyException();
        verifyNoInteractions(openAuthTemplate);
    }

    @Test
    void isBlacklisted_WhenOnlyTheAuthRoleIsOpen_ReadsTheReplica() {
        when(readTemplate.opsForValue()).thenReturn(readValues);
        when(readValues.get(anyString())).thenReturn(IMMEDIATE_MARKER + "0");

        assertThat(serviceWithTheAuthRoleOpen().isBlacklisted("revoked.jwt")).isTrue();
        verifyNoInteractions(openAuthTemplate);
    }

    private TokenBlacklistService serviceWithTheAuthRoleOpen() {
        RedisRoleHealth authOpen = RoleOutages.withOpen(RedisRole.AUTH);
        return new TokenBlacklistService(AuthReads.writesTo(openAuthTemplate, authOpen),
                AuthReads.localThenAuth(readTemplate, openAuthTemplate, authOpen), new SimpleMeterRegistry(),
                TokenBlacklistService.DEFAULT_REFRESH_TOKEN_EXPIRY_MS);
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
