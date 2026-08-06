package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes which side of a commit each cross-store effect runs on.
 *
 * <p>MySQL and Redis share no transaction, so ordering substitutes for atomicity and the order is
 * chosen per effect by which stale combination is survivable. Observer effects — SSE publishes —
 * follow the commit in an {@code @TransactionalEventListener(AFTER_COMMIT)} listener, because an
 * inline publish announces rows to subscribers on every rollback path. Guard effects — token
 * revocation — precede the commit so their failure aborts it, because a deleted account with live
 * tokens has no durable record to retry from.</p>
 *
 * <p>The rule below is the negative form of that split: a transactional method reaches no
 * cross-store effect, except the guard calls named in {@link #GUARDS_ALLOWED_INLINE}. Reachability
 * is transitive through non-transactional helpers, because a private helper is how the ban would
 * otherwise be evaded; a transactional callee ends the walk since it is checked as its own root.</p>
 */
class EffectPlacementTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.danteplanner.backend");

    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    private static final String SSE_PUBLISHER = "org.danteplanner.backend.shared.sse.SsePublisher";

    private static final String TOKEN_BLACKLIST_SERVICE =
            "org.danteplanner.backend.auth.token.TokenBlacklistService";

    /**
     * The cross-store effects the rule watches, as owner type to method names. Every {@code
     * SsePublisher} member is an observer publish; the notification announce path reaches Redis
     * only through it, so naming the publisher covers both.
     */
    private static final Map<String, Set<String>> OBSERVER_PUBLISHES =
            Map.of(SSE_PUBLISHER, Set.of("*"));

    private static final Map<String, Set<String>> GUARD_EFFECTS =
            Map.of(TOKEN_BLACKLIST_SERVICE, Set.of("invalidateUserTokens"));

    /**
     * The transactional methods permitted to reach a guard effect inline.
     *
     * <p>Both revoke the deleted account's tokens before the commit that deletes it: auth is
     * token-only, so a revocation deferred past the commit leaves a window where a deleted account
     * still authenticates, and a crash inside that window leaves no record to retry from.</p>
     */
    private static final Set<String> GUARDS_ALLOWED_INLINE = Set.of(
            "org.danteplanner.backend.user.service.UserAccountLifecycleService.deleteAccount",
            "org.danteplanner.backend.user.service.UserAccountLifecycleService.performHardDelete");

    @Test
    @DisplayName("a transactional method reaches no observer publish, and no guard outside the allowlist")
    void crossStoreEffect_WhenReachedFromATransactionalMethod_IsRejected() {
        Set<String> offenders = new TreeSet<>();

        for (JavaMethod root : transactionalMethods()) {
            String rootName = qualifiedName(root);
            for (String target : effectsReachableFrom(root)) {
                boolean allowedGuard = target.startsWith(TOKEN_BLACKLIST_SERVICE)
                        && GUARDS_ALLOWED_INLINE.contains(rootName);
                if (!allowedGuard) {
                    offenders.add(rootName + " -> " + target);
                }
            }
        }

        assertThat(offenders)
                .as("an observer publish inside a transaction announces rows that a rollback then "
                        + "discards; publish an event and move the call into a "
                        + "@TransactionalEventListener(AFTER_COMMIT) listener. A guard belongs "
                        + "inline, but a new one is a deliberate decision: add it to "
                        + "GUARDS_ALLOWED_INLINE with the reason it cannot be deferred")
                .isEmpty();
    }

    @Test
    @DisplayName("every allowlisted guard still performs its inline call")
    void guardAllowlist_WhenScanned_NamesOnlyLiveInlineCalls() {
        Set<String> stale = new TreeSet<>(GUARDS_ALLOWED_INLINE);

        for (JavaMethod root : transactionalMethods()) {
            if (effectsReachableFrom(root).stream().anyMatch(t -> t.startsWith(TOKEN_BLACKLIST_SERVICE))) {
                stale.remove(qualifiedName(root));
            }
        }

        assertThat(stale)
                .as("these no longer reach a guard effect, so the allowlist now grants permission "
                        + "nobody uses; delete the entry")
                .isEmpty();
    }

    private static List<JavaMethod> transactionalMethods() {
        return MAIN_CLASSES.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .filter(EffectPlacementTest::isTransactional)
                .toList();
    }

    private static boolean isTransactional(JavaMethod method) {
        return method.isAnnotatedWith(TRANSACTIONAL) || method.getOwner().isAnnotatedWith(TRANSACTIONAL);
    }

    /**
     * Walks outward from a transactional method through its non-transactional callees, collecting
     * every cross-store effect it can reach.
     *
     * @param root the transactional method to walk from
     * @return the reached effects as {@code owner.method}, empty when the method reaches none
     */
    private static List<String> effectsReachableFrom(JavaMethod root) {
        List<String> reached = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        pending.push(root);
        visited.add(qualifiedName(root));

        while (!pending.isEmpty()) {
            for (JavaMethodCall call : pending.pop().getMethodCallsFromSelf()) {
                String owner = call.getTargetOwner().getFullName();
                String name = call.getTarget().getName();

                if (isEffect(OBSERVER_PUBLISHES, owner, name) || isEffect(GUARD_EFFECTS, owner, name)) {
                    reached.add(owner + "." + name);
                    continue;
                }
                if (!owner.startsWith("org.danteplanner.backend")) {
                    continue;
                }
                Optional<JavaMethod> callee = call.getTarget().resolveMember();
                if (callee.isEmpty() || isTransactional(callee.get())) {
                    continue;
                }
                if (visited.add(qualifiedName(callee.get()))) {
                    pending.push(callee.get());
                }
            }
        }
        return reached;
    }

    private static boolean isEffect(Map<String, Set<String>> effects, String owner, String methodName) {
        Set<String> members = effects.get(owner);
        return members != null && (members.contains("*") || members.contains(methodName));
    }

    private static String qualifiedName(JavaCodeUnit method) {
        return method.getOwner().getFullName() + "." + method.getName();
    }
}
