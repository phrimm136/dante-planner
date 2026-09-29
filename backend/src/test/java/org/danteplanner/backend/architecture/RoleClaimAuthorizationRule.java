package org.danteplanner.backend.architecture;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import jakarta.servlet.http.HttpServletRequest;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.shared.security.AccessTokenAuthenticator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class RoleClaimAuthorizationRule {

    private static final Set<String> ROLE_CLAIM_READERS = Set.of("getEffectiveRole", "role");

    private static final Set<String> ROLE_CLAIM_READING_CLASSES = Set.of(
            AccessTokenAuthenticator.class.getName(),
            RefreshRotationService.class.getName());

    private static final String IS_USER_IN_ROLE = "isUserInRole";

    private static final String GET_AUTHORITIES = "getAuthorities";

    private static final String CONSTRUCTOR = "<init>";

    private static final String SPRING_SECURITY_PACKAGE = "org.springframework.security";

    private static final Set<String> ROLE_CHECK_METHODS =
            Set.of("hasRole", "hasAnyRole", "hasAuthority", "hasAnyAuthority");

    private static final List<Class<? extends Annotation>> ROLE_CHECK_ANNOTATIONS =
            List.of(PreAuthorize.class, PostAuthorize.class, Secured.class);

    private static final List<String> SECURITY_CHAIN_PACKAGES = List.of(
            "org.danteplanner.backend.shared.security",
            "org.danteplanner.backend.shared.config");

    static final ArchRule ONLY_THE_AUTHENTICATOR_READS_THE_ROLE_CLAIM =
            classes()
                    .should(accessNothingWhere(
                            RoleClaimAuthorizationRule::readsTheRoleClaim,
                            type -> ROLE_CLAIM_READING_CLASSES.contains(type.getName()),
                            "reads the token's role claim outside AccessTokenAuthenticator"))
                    .as("only AccessTokenAuthenticator reads TokenClaims.role() or getEffectiveRole(),"
                            + " besides RefreshRotationService, which copies the claim into an admitted"
                            + " legacy token without authorizing on it");

    static final ArchRule NO_ROLE_CHECK_THROUGH_SPRING_SECURITY =
            classes()
                    .should(declareNoRoleCheckAnnotation())
                    .andShould(accessNothingWhere(
                            RoleClaimAuthorizationRule::isASpringSecurityRoleCheck,
                            type -> false,
                            "authorizes on granted authorities through Spring Security"))
                    .as("no class authorizes through hasRole, hasAnyRole, hasAuthority, hasAnyAuthority,"
                            + " HttpServletRequest.isUserInRole, @PreAuthorize, @PostAuthorize or @Secured");

    static final ArchRule ONLY_THE_SECURITY_CHAIN_READS_GRANTED_AUTHORITIES =
            classes()
                    .should(accessNothingWhere(
                            RoleClaimAuthorizationRule::readsGrantedAuthorities,
                            RoleClaimAuthorizationRule::belongsToTheSecurityChain,
                            "reads granted authorities outside the security chain"))
                    .as("no class outside shared.security and shared.config reads GrantedAuthority"
                            + " or Authentication.getAuthorities()");

    private RoleClaimAuthorizationRule() {
    }

    private static ArchCondition<JavaClass> accessNothingWhere(
            Predicate<AccessTarget> forbidden, Predicate<JavaClass> allowed, String violation) {
        return new ArchCondition<>("access nothing that " + violation) {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                if (allowed.test(type)) {
                    return;
                }
                for (JavaAccess<?> access : type.getAccessesFromSelf()) {
                    if (forbidden.test(access.getTarget())) {
                        events.add(SimpleConditionEvent.violated(access,
                                type.getName() + " " + violation + ": " + access.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> declareNoRoleCheckAnnotation() {
        return new ArchCondition<>("declare no role-check annotation") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                for (Class<? extends Annotation> annotation : ROLE_CHECK_ANNOTATIONS) {
                    if (type.isAnnotatedWith(annotation)) {
                        events.add(SimpleConditionEvent.violated(type, type.getName()
                                + " is annotated with @" + annotation.getSimpleName()));
                    }
                    type.getMethods().stream()
                            .filter(method -> method.isAnnotatedWith(annotation))
                            .forEach(method -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " is annotated with @" + annotation.getSimpleName())));
                }
            }
        };
    }

    private static boolean readsTheRoleClaim(AccessTarget target) {
        return target instanceof AccessTarget.CodeUnitAccessTarget
                && target.getOwner().isEquivalentTo(TokenClaims.class)
                && ROLE_CLAIM_READERS.contains(target.getName());
    }

    private static boolean isASpringSecurityRoleCheck(AccessTarget target) {
        return (target.getOwner().getPackageName().startsWith(SPRING_SECURITY_PACKAGE)
                        && ROLE_CHECK_METHODS.contains(target.getName()))
                || (target.getOwner().isAssignableTo(HttpServletRequest.class)
                        && target.getName().equals(IS_USER_IN_ROLE));
    }

    private static boolean readsGrantedAuthorities(AccessTarget target) {
        JavaClass owner = target.getOwner();
        return (owner.isAssignableTo(GrantedAuthority.class) && !target.getName().equals(CONSTRUCTOR))
                || (owner.isAssignableTo(Authentication.class) && target.getName().equals(GET_AUTHORITIES));
    }

    private static boolean belongsToTheSecurityChain(JavaClass type) {
        return SECURITY_CHAIN_PACKAGES.stream()
                .anyMatch(chain -> type.getPackageName().equals(chain)
                        || type.getPackageName().startsWith(chain + "."));
    }
}
