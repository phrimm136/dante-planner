package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes which side of a commit each cross-store effect runs on.
 *
 * <p>MySQL and Redis share no transaction, so ordering substitutes for atomicity and the order is
 * chosen per effect by which stale combination is survivable. Observer effects — SSE publishes —
 * follow a commit, because an inline publish announces rows to subscribers on every rollback path
 * and the frontend patches its cache straight from the envelope. Guard effects — token revocation —
 * precede the commit so their failure aborts it, because a deleted account with live tokens has no
 * durable record to retry from.</p>
 *
 * <p>An observer effect reaches that after-commit position through the outbox: the causing
 * transaction commits a {@code domain_events} row, {@code DomainEventDispatcher} derives the effect
 * in a transaction of its own, and the arm enqueues its pushes on an {@code EffectPushQueue} that
 * an after-commit synchronization releases. The two remaining after-commit listeners that publish
 * directly are suspension and settings invalidation, which carry no durable row.</p>
 *
 * <p>The rules below are the negative form of that split, and each is stated at the width it can
 * actually check:</p>
 *
 * <ul>
 *   <li>a transactional method's call graph reaches no cross-store effect, except the guard calls
 *       named in {@link #GUARDS_ALLOWED_INLINE}. Reachability is transitive through
 *       non-transactional helpers and resolves interface calls onto their implementations, because
 *       a private helper and an interface hop are both how the ban would otherwise be evaded; a
 *       transactional callee ends the walk since it is checked as its own root;</li>
 *   <li>no arm depends on {@code SsePublisher} at all. That is what makes the first rule true of
 *       the outbox rather than merely unreached by it — an arm that published would be caught by
 *       the walk, and an arm that cannot name the publisher cannot publish.</li>
 * </ul>
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
                .as("an observer publish inside a transaction announces a row that a rollback then "
                        + "discards, and the recipient's cache keeps it; record a domain event and "
                        + "let an effect arm enqueue the push, which the dispatcher releases only "
                        + "once its commit is durable. A guard belongs inline, but a new one is a "
                        + "deliberate decision: add it to GUARDS_ALLOWED_INLINE with the reason it "
                        + "cannot be deferred")
                .isEmpty();
    }

    @Test
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

    private static final String TRANSACTIONAL_EVENT_LISTENER =
            "org.springframework.transaction.event.TransactionalEventListener";

    private static final String DOMAIN_EVENT_DISPATCHER =
            "org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher";

    private static final String DOMAIN_EFFECT =
            "org.danteplanner.backend.shared.outbox.service.DomainEffectHandler";

    private static final String NOTIFICATION_DISPATCH_SERVICE =
            "org.danteplanner.backend.notification.service.NotificationDispatchService";

    private static final String NOTIFICATIONS = "org.danteplanner.backend.notification.api.Notifications";

    private static final Set<String> RAISE_OWNERS = Set.of(NOTIFICATIONS, NOTIFICATION_DISPATCH_SERVICE);

    private static final String NOTIFICATION_REPOSITORY =
            "org.danteplanner.backend.notification.repository.NotificationRepository";

    private static final String EAGER_DISPATCH =
            "org.danteplanner.backend.shared.outbox.service.DomainEventEagerDispatch";

    private static final String NOTIFICATION_PACKAGE = "org.danteplanner.backend.notification";

    /**
     * The methods that derive a notification row, as the arms call them. Their placement is the
     * whole claim: a notification exists because a committed event row said so, never because a
     * request thread was passing.
     */
    private static final Set<String> RAISE_METHODS = Set.of(
            "notifyPlannerPublished", "notifyPlannerRecommended",
            "notifyCommentReceived", "notifyReplyReceived");

    private static final Set<String> NOTIFICATION_WRITE_METHODS = Set.of(
            "insert", "insertIgnore", "insertPublishedFanout", "markAsRead", "markAllAsRead",
            "softDeleteOldReadNotifications", "hardDeleteOldNotifications", "softDeleteAllByUserId");

    @Test
    void afterCommitListener_WhenItDerivesANotification_IsRejected() {
        Set<String> offenders = afterCommitListeners().stream()
                .filter(listener -> RAISE_OWNERS.stream().anyMatch(owner -> reaches(listener, owner)))
                .map(EffectPlacementTest::qualifiedName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(offenders)
                .as("a listener that writes its own notification loses the push and the row "
                        + "together when the process dies in the commit-to-listener window; record "
                        + "a domain event inside the causing transaction and let an arm derive it")
                .isEmpty();
    }

    @Test
    void outboxDispatch_WhenReachedFromAfterCommit_ComesOnlyFromTheEagerHop() {
        Set<String> reaching = afterCommitListeners().stream()
                .filter(listener -> reaches(listener, DOMAIN_EVENT_DISPATCHER))
                .map(EffectPlacementTest::qualifiedName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(reaching)
                .as("the eager hop is the one listener the outbox has; a second entry point into "
                        + "the dispatcher is a second place its idempotence has to be reasoned "
                        + "about")
                .containsExactly(EAGER_DISPATCH + ".onDomainEventRecorded");
    }

    @Test
    void notificationWrite_WhenIssuedOutsideTheFeature_IsRejected() {
        Set<String> offenders = new TreeSet<>();

        for (JavaClass caller : MAIN_CLASSES) {
            if (caller.getPackageName().startsWith(NOTIFICATION_PACKAGE)) {
                continue;
            }
            for (JavaMethodCall call : caller.getMethodCallsFromSelf()) {
                if (NOTIFICATION_REPOSITORY.equals(call.getTargetOwner().getFullName())
                        && NOTIFICATION_WRITE_METHODS.contains(call.getTarget().getName())) {
                    offenders.add(caller.getFullName() + " -> " + call.getTarget().getName());
                }
            }
        }

        assertThat(offenders)
                .as("the notification row is the notification feature's to write; an effect arm "
                        + "goes through NotificationDispatchService, which is what keeps the "
                        + "INSERT IGNORE derivation in one place")
                .isEmpty();
    }

    @Test
    void raiseMethod_WhenCalledOutsideAnEffectArm_IsRejected() {
        Set<String> offenders = new TreeSet<>();

        for (JavaClass caller : MAIN_CLASSES) {
            if (caller.getPackageName().contains(".effect")
                    || DOMAIN_EVENT_DISPATCHER.equals(topLevel(caller))
                    || NOTIFICATION_DISPATCH_SERVICE.equals(topLevel(caller))) {
                continue;
            }
            for (JavaMethodCall call : caller.getMethodCallsFromSelf()) {
                if (RAISE_OWNERS.contains(call.getTargetOwner().getFullName())
                        && RAISE_METHODS.contains(call.getTarget().getName())) {
                    offenders.add(caller.getFullName() + " -> " + call.getTarget().getName());
                }
            }
        }

        assertThat(offenders)
                .as("a notification is owed by a committed domain event, so the only caller is the "
                        + "arm the dispatcher selected; record an event instead")
                .isEmpty();
    }

    @Test
    void effectArm_WhenDeclaredOutsideAnEffectPackage_IsRejected() {
        Set<String> offenders = MAIN_CLASSES.stream()
                .filter(javaClass -> javaClass.getAllRawInterfaces().stream()
                        .anyMatch(implemented -> DOMAIN_EFFECT.equals(implemented.getFullName())))
                .filter(javaClass -> !javaClass.getPackageName().contains(".effect"))
                .map(JavaClass::getFullName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(offenders)
                .as("an arm belongs to the feature that owns the rows it writes, in that feature's "
                        + "effect package; shared/outbox carries the mechanism and no feature")
                .isEmpty();
    }

    @Test
    void effectArm_WhenItDependsOnThePublisher_IsRejected() {
        Set<String> offenders = MAIN_CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().contains(".effect"))
                .filter(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency ->
                                SSE_PUBLISHER.equals(dependency.getTargetClass().getFullName())))
                .map(JavaClass::getFullName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(offenders)
                .as("an arm runs inside the dispatch transaction, so a publish it made would "
                        + "announce a row the dispatch may still roll back; enqueue on the "
                        + "EffectPushQueue the dispatcher hands it, which is released after commit")
                .isEmpty();
    }

    /**
     * The methods a call can land on: the declared target, plus every implementation of it in this
     * tree when the target is an interface or abstract.
     *
     * <p>Without the implementations the walk dead-ends at every seam, and a rule that stops at the
     * first interface reports compliance for code it never looked at.</p>
     *
     * @param call the call to resolve
     * @return the concrete methods reachable through it
     */
    private static List<JavaMethod> calleesOf(JavaMethodCall call) {
        Optional<JavaMethod> declared = call.getTarget().resolveMember();
        if (declared.isEmpty()) {
            return List.of();
        }
        JavaMethod target = declared.get();
        if (!target.getOwner().isInterface() && !target.getModifiers().contains(JavaModifier.ABSTRACT)) {
            return List.of(target);
        }

        List<JavaMethod> resolved = new ArrayList<>();
        resolved.add(target);
        for (JavaClass implementation : target.getOwner().getAllSubclasses()) {
            implementation.getMethods().stream()
                    .filter(method -> method.getName().equals(target.getName()))
                    .filter(method -> method.getRawParameterTypes()
                            .equals(target.getRawParameterTypes()))
                    .forEach(resolved::add);
        }
        return resolved;
    }

    private static String topLevel(JavaClass javaClass) {
        String fullName = javaClass.getFullName();
        int nested = fullName.indexOf('$');
        return nested < 0 ? fullName : fullName.substring(0, nested);
    }

    private static List<JavaMethod> afterCommitListeners() {
        return MAIN_CLASSES.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(TRANSACTIONAL_EVENT_LISTENER))
                .toList();
    }

    /**
     * Whether a method reaches a type through the helpers between them.
     *
     * @param root  the method to walk from
     * @param owner the fully qualified type to look for
     * @return true when some call chain from the method lands on that type
     */
    private static boolean reaches(JavaMethod root, String owner) {
        Set<String> visited = new HashSet<>();
        Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        pending.push(root);
        visited.add(qualifiedName(root));

        while (!pending.isEmpty()) {
            for (JavaMethodCall call : pending.pop().getMethodCallsFromSelf()) {
                String target = call.getTargetOwner().getFullName();
                if (target.equals(owner)) {
                    return true;
                }
                if (!target.startsWith("org.danteplanner.backend")) {
                    continue;
                }
                Optional<JavaMethod> callee = call.getTarget().resolveMember();
                if (callee.isPresent() && visited.add(qualifiedName(callee.get()))) {
                    pending.push(callee.get());
                }
            }
        }
        return false;
    }

    private static List<JavaMethod> transactionalMethods() {
        return transactionalMethodsIn(MAIN_CLASSES);
    }

    private static List<JavaMethod> transactionalMethodsIn(JavaClasses classes) {
        return classes.stream()
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
                for (JavaMethod callee : calleesOf(call)) {
                    if (isTransactional(callee)) {
                        continue;
                    }
                    if (visited.add(qualifiedName(callee))) {
                        pending.push(callee);
                    }
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

    private static final Map<String, Set<String>> TOMBSTONE_WRITES = Map.of(
            "org.danteplanner.backend.shared.readpath.ContentTombstoneStore",
            Set.of("writeTombstone", "writeTombstones", "writeTombstoneOrThrow", "clearTombstoneOrThrow"));

    private static final Set<String> ADMITTED_TOMBSTONE_WRITERS = Set.of(
            "org.danteplanner.backend.planner.service.PlannerCatalogSyncService.syncPlanner");

    private static final String ADMITTED_WRITER_CALLER =
            "org.danteplanner.backend.planner.service.PlannerCatalogSyncService.drainPending";

    private static final String FIXTURE_PACKAGE = "org.danteplanner.backend.architecture.fixture";

    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter()
            .importPackages(FIXTURE_PACKAGE);

    private static final Set<String> FIXTURE_ADMITTED_WRITERS = Set.of(
            FIXTURE_PACKAGE + ".TombstoneWritingService.syncFromCommittedState");

    private static final String FIXTURE_WRITER_CALLER = FIXTURE_PACKAGE + ".TombstoneWritingService.drainPending";

    @Test
    void tombstoneWrite_WhenReachedFromATransactionalMethodOtherThanTheCatalogSync_IsRejected() {
        assertThat(tombstoneOffendersIn(MAIN_CLASSES, ADMITTED_TOMBSTONE_WRITERS))
                .as("a tombstone written before the commit outlives a rollback and masks a live row "
                        + "for its whole TTL; register it in an afterCommit synchronization. Only the "
                        + "catalog sync may write inside a transaction, because it writes what state "
                        + "already committed implies")
                .isEmpty();
    }

    @Test
    void admittedTombstoneWriter_WhenScanned_IsLiveAndCalledOnlyByTheDrain() {
        assertThat(ADMITTED_TOMBSTONE_WRITERS)
                .as("an admitted writer that no longer reaches a tombstone write grants permission "
                        + "nobody uses; delete the entry")
                .allSatisfy(name -> assertThat(transactionalMethodsIn(MAIN_CLASSES).stream()
                        .filter(method -> qualifiedName(method).equals(name))
                        .anyMatch(method -> !tombstoneWritesReachedFrom(method).isEmpty())).isTrue());
        assertThat(callersOtherThan(MAIN_CLASSES, ADMITTED_TOMBSTONE_WRITERS, ADMITTED_WRITER_CALLER))
                .as("the admitted sync writes before its commit, which is safe only from the catalog "
                        + "drain deriving from committed state; a request path must record an event")
                .isEmpty();
    }

    @Test
    void tombstoneWrite_WhenTheRuleRunsOverTheFixture_NamesOnlyTheUnadmittedPaths() {
        assertThat(tombstoneOffendersIn(FIXTURE_CLASSES, FIXTURE_ADMITTED_WRITERS))
                .as("the rules above pass vacuously unless these show them rejecting a write")
                .containsExactly(FIXTURE_PACKAGE + ".TombstoneWritingService.hideBeforeCommit -> "
                        + "org.danteplanner.backend.shared.readpath.ContentTombstoneStore.writeTombstoneOrThrow");
        assertThat(callersOtherThan(FIXTURE_CLASSES, FIXTURE_ADMITTED_WRITERS, FIXTURE_WRITER_CALLER))
                .containsExactly(FIXTURE_PACKAGE + ".TombstoneWritingService.syncFromElsewhere -> "
                        + FIXTURE_PACKAGE + ".TombstoneWritingService.syncFromCommittedState");
    }

    private static Set<String> tombstoneOffendersIn(JavaClasses classes, Set<String> admitted) {
        Set<String> offenders = new TreeSet<>();
        for (JavaMethod root : transactionalMethodsIn(classes)) {
            if (admitted.contains(qualifiedName(root))) {
                continue;
            }
            tombstoneWritesReachedFrom(root).forEach(target -> offenders.add(qualifiedName(root) + " -> " + target));
        }
        return offenders;
    }

    private static List<String> tombstoneWritesReachedFrom(JavaMethod root) {
        List<String> reached = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        pending.push(root);
        visited.add(qualifiedName(root));

        while (!pending.isEmpty()) {
            for (JavaMethodCall call : pending.pop().getMethodCallsFromSelf()) {
                String owner = call.getTargetOwner().getFullName();
                if (isEffect(TOMBSTONE_WRITES, owner, call.getTarget().getName())) {
                    reached.add(owner + "." + call.getTarget().getName());
                    continue;
                }
                if (!owner.startsWith("org.danteplanner.backend")) {
                    continue;
                }
                for (JavaMethod callee : calleesOf(call)) {
                    if (!isTransactional(callee) && visited.add(qualifiedName(callee))) {
                        pending.push(callee);
                    }
                }
            }
        }
        return reached;
    }

    private static Set<String> callersOtherThan(JavaClasses classes, Set<String> admitted, String allowedCaller) {
        Set<String> offenders = new TreeSet<>();
        for (JavaClass caller : classes) {
            for (JavaMethodCall call : caller.getMethodCallsFromSelf()) {
                String target = call.getTargetOwner().getFullName() + "." + call.getTarget().getName();
                String origin = qualifiedName(call.getOrigin());
                if (admitted.contains(target) && !origin.equals(allowedCaller)) {
                    offenders.add(origin + " -> " + target);
                }
            }
        }
        return offenders;
    }
}
