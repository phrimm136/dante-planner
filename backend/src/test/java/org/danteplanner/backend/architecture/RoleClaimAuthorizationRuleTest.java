package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.shared.security.AccessTokenAuthenticator;
import org.danteplanner.roleclaimfixture.AuthorityNameReferenceFixture;
import org.danteplanner.roleclaimfixture.AuthorityReaderFixture;
import org.danteplanner.roleclaimfixture.EffectiveRoleReaderFixture;
import org.danteplanner.roleclaimfixture.PreAuthorizedHandlerFixture;
import org.danteplanner.roleclaimfixture.RoleAccessorReaderFixture;
import org.danteplanner.roleclaimfixture.RoleDslFixture;
import org.danteplanner.roleclaimfixture.UserInRoleFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleClaimAuthorizationRuleTest {

    @Test
    void effectiveRoleReader_WhenOutsideTheAuthenticator_FailsNamingTheClass() {
        assertFailsNaming(RoleClaimAuthorizationRule.ONLY_THE_AUTHENTICATOR_READS_THE_ROLE_CLAIM,
                EffectiveRoleReaderFixture.class, "getEffectiveRole");
    }

    @Test
    void roleAccessorReader_WhenOutsideTheAuthenticator_FailsNamingTheClass() {
        assertFailsNaming(RoleClaimAuthorizationRule.ONLY_THE_AUTHENTICATOR_READS_THE_ROLE_CLAIM,
                RoleAccessorReaderFixture.class, "role()");
    }

    @Test
    void userInRoleCheck_WhenCalled_FailsNamingTheClass() {
        assertFailsNaming(RoleClaimAuthorizationRule.NO_ROLE_CHECK_THROUGH_SPRING_SECURITY,
                UserInRoleFixture.class, "isUserInRole");
    }

    @Test
    void hasRoleInTheRequestDsl_WhenCalled_FailsNamingTheClass() {
        assertFailsNaming(RoleClaimAuthorizationRule.NO_ROLE_CHECK_THROUGH_SPRING_SECURITY,
                RoleDslFixture.class, "hasRole");
    }

    @Test
    void preAuthorizeOnAMethod_WhenDeclared_FailsNamingTheMethod() {
        assertFailsNaming(RoleClaimAuthorizationRule.NO_ROLE_CHECK_THROUGH_SPRING_SECURITY,
                PreAuthorizedHandlerFixture.class, "hide()");
    }

    @Test
    void authoritiesReader_WhenOutsideTheSecurityChain_FailsNamingTheClass() {
        assertFailsNaming(RoleClaimAuthorizationRule.ONLY_THE_SECURITY_CHAIN_READS_GRANTED_AUTHORITIES,
                AuthorityReaderFixture.class, "getAuthorities");
    }

    @Test
    void authorityMethodReference_WhenOutsideTheSecurityChain_FailsNamingTheClass() {
        assertFailsNaming(RoleClaimAuthorizationRule.ONLY_THE_SECURITY_CHAIN_READS_GRANTED_AUTHORITIES,
                AuthorityNameReferenceFixture.class, "getAuthority");
    }

    @Test
    void accessTokenAuthenticator_WhenItReadsTheClaimAndBuildsAuthorities_PassesEveryRule() {
        JavaClasses authenticator = new ClassFileImporter().importClasses(AccessTokenAuthenticator.class);

        assertThatCode(() -> {
            RoleClaimAuthorizationRule.ONLY_THE_AUTHENTICATOR_READS_THE_ROLE_CLAIM.check(authenticator);
            RoleClaimAuthorizationRule.NO_ROLE_CHECK_THROUGH_SPRING_SECURITY.check(authenticator);
            RoleClaimAuthorizationRule.ONLY_THE_SECURITY_CHAIN_READS_GRANTED_AUTHORITIES.check(authenticator);
        }).doesNotThrowAnyException();
    }

    @Test
    void refreshRotationService_WhenItCopiesTheRoleClaim_PassesTheRoleClaimRule() {
        JavaClasses rotation = new ClassFileImporter().importClasses(RefreshRotationService.class);

        assertThatCode(() -> RoleClaimAuthorizationRule.ONLY_THE_AUTHENTICATOR_READS_THE_ROLE_CLAIM.check(rotation))
                .doesNotThrowAnyException();
    }

    private static void assertFailsNaming(ArchRule rule, Class<?> fixture, String member) {
        JavaClasses violator = new ClassFileImporter().importClasses(fixture);

        assertThatThrownBy(() -> rule.check(violator))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(fixture.getName())
                        .contains(member));
    }
}
