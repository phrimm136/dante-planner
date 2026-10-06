package org.danteplanner.backend.support;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.user.api.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

public interface RefreshRotationContract {

    long REUSE_WINDOW_MS = RecordingRefreshRotation.DEFAULT_RETRY_REUSE_WINDOW_MS;
    long NO_REUSE_WINDOW_MS = 0L;
    Duration ISSUE_SKEW = Duration.ofMinutes(1);

    TestJwt jwt();

    RefreshRotationService rotation(boolean legacyAdmitEnabled, long retryReuseWindowMs);

    TokenBlacklistService blacklist();

    Long freshUserId();

    private RefreshRotationService rotation() {
        return rotation(true, REUSE_WINDOW_MS);
    }

    private String freshLogin(Long userId) {
        return jwt().service().generateRefreshToken(userId);
    }

    private TokenClaims claimsOf(String token) {
        return jwt().service().validateRefreshToken(token);
    }

    private static RotationResult.Rotated rotated(RotationResult result) {
        assertThat(result).isInstanceOf(RotationResult.Rotated.class);
        return (RotationResult.Rotated) result;
    }

    @Test
    default void rotate_WhenAFreshLoginTokenIsPresented_RotatesToASuccessorInTheSameFamily() {
        Long userId = freshUserId();
        String login = freshLogin(userId);
        TokenClaims presented = claimsOf(login);

        RotationResult.Rotated result = rotated(rotation().rotate(login));

        assertThat(claimsOf(result.newRefreshJwt())).isEqualTo(result.claims());
        assertThat(result.claims().userId()).isEqualTo(userId);
        assertThat(result.claims().familyId()).isEqualTo(presented.familyId());
        assertThat(result.claims().parentJti()).isEqualTo(presented.jti());
        assertThat(result.claims().jti()).isNotEqualTo(presented.jti());
    }

    @Test
    default void rotate_WhenTheSameTokenIsRetriedWithinTheWindow_HandsBackTheSameSuccessor() {
        RefreshRotationService rotation = rotation();
        String login = freshLogin(freshUserId());
        RotationResult.Rotated first = rotated(rotation.rotate(login));

        RotationResult.Rotated retry = rotated(rotation.rotate(login));

        assertThat(retry.newRefreshJwt()).isEqualTo(first.newRefreshJwt());
    }

    @Test
    default void rotate_WhenTheSameTokenIsRetriedOutsideTheWindow_SupersedesTheFirstSuccessor() {
        RefreshRotationService rotation = rotation(true, NO_REUSE_WINDOW_MS);
        String login = freshLogin(freshUserId());
        RotationResult.Rotated first = rotated(rotation.rotate(login));

        RotationResult.Rotated retry = rotated(rotation.rotate(login));

        assertThat(retry.newRefreshJwt()).isNotEqualTo(first.newRefreshJwt());
        assertThat(rotation.rotate(first.newRefreshJwt()))
                .isEqualTo(new RotationResult.Revoked(claimsOf(login).familyId()));
    }

    @Test
    default void rotate_WhenASuccessorIsUsed_LetsItRotateAgain() {
        RefreshRotationService rotation = rotation();
        String login = freshLogin(freshUserId());
        RotationResult.Rotated second = rotated(rotation.rotate(login));

        RotationResult.Rotated third = rotated(rotation.rotate(second.newRefreshJwt()));

        assertThat(third.claims().parentJti()).isEqualTo(second.claims().jti());
        assertThat(third.claims().familyId()).isEqualTo(second.claims().familyId());
    }

    @Test
    default void rotate_WhenARetiredParentIsReplayed_RevokesTheWholeFamily() {
        RefreshRotationService rotation = rotation();
        String login = freshLogin(freshUserId());
        RotationResult.Rotated second = rotated(rotation.rotate(login));
        RotationResult.Rotated third = rotated(rotation.rotate(second.newRefreshJwt()));

        RotationResult replay = rotation.rotate(login);

        String familyId = claimsOf(login).familyId();
        assertThat(replay).isEqualTo(new RotationResult.Revoked(familyId));
        assertThat(rotation.rotate(third.newRefreshJwt()))
                .isEqualTo(new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY));
    }

    @Test
    default void rotate_WhenTheFamilyWasRevoked_RejectsItsTokens() {
        RefreshRotationService rotation = rotation();
        String login = freshLogin(freshUserId());

        rotation.revokeFamily(claimsOf(login).familyId());

        assertThat(rotation.rotate(login))
                .isEqualTo(new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY));
    }

    @Test
    default void rotate_WhenLogoutRevokedTheFamily_RejectsItsTokens() {
        RefreshRotationService rotation = rotation();
        String login = freshLogin(freshUserId());

        blacklist().revokeLogoutSession(List.of(new LogoutRevocation.FamilyRevocation(claimsOf(login).familyId())));

        assertThat(rotation.rotate(login))
                .isEqualTo(new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY));
    }

    @Test
    default void rotate_WhenTheUserWasInvalidatedAfterTheTokenWasIssued_RejectsIt() {
        Long userId = freshUserId();
        jwt().clock().advance(ISSUE_SKEW.negated());
        String login = freshLogin(userId);
        jwt().clock().advance(ISSUE_SKEW);

        blacklist().invalidateUserTokens(userId);

        assertThat(rotation().rotate(login))
                .isEqualTo(new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY));
    }

    @Test
    default void rotate_WhenTheTokenWasIssuedAfterTheInvalidation_Rotates() {
        Long userId = freshUserId();
        blacklist().invalidateUserTokens(userId);
        jwt().clock().advance(ISSUE_SKEW);

        String login = freshLogin(userId);

        assertThat(rotation().rotate(login)).isInstanceOf(RotationResult.Rotated.class);
    }

    @Test
    default void rotate_WhenTheTokenIsNotAJwt_RejectsItAsInvalid() {
        assertThat(rotation().rotate("not-a-jwt"))
                .isEqualTo(new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID));
    }

    @Test
    default void rotate_WhenAnAccessTokenIsPresented_RejectsItAsInvalid() {
        String access = jwt().service().generateAccessToken(freshUserId(), UserRole.NORMAL);

        assertThat(rotation().rotate(access))
                .isEqualTo(new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID));
    }

    @Test
    default void rotate_WhenALegacyTokenIsAdmitted_RotatesIntoItsSynthesizedFamily() {
        Long userId = freshUserId();
        String legacy = jwt().legacyRefreshToken(userId);
        long issuedAtMs = claimsOf(legacy).issuedAt().getTime();

        RotationResult.Rotated result = rotated(rotation().rotate(legacy));

        assertThat(result.claims().familyId()).isEqualTo(RefreshRotationService.legacyFamilyId(userId, issuedAtMs));
        assertThat(result.claims().parentJti()).isNotNull();
    }

    @Test
    default void rotate_WhenALegacyTokenIsPresentedWithAdmissionOff_RejectsItAsInvalid() {
        String legacy = jwt().legacyRefreshToken(freshUserId());

        assertThat(rotation(false, REUSE_WINDOW_MS).rotate(legacy))
                .isEqualTo(new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID));
    }
}
