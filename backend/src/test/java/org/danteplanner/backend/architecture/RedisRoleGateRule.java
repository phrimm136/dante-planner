package org.danteplanner.backend.architecture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;

import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RoleGated;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class RedisRoleGateRule {

    static final Set<String> ROLE_HEALTH_READERS = Set.of(
            "org.danteplanner.backend.shared.freshness.MarkerStore");

    static final Set<String> STORE_DEFINITIONS = Set.of(
            "org.danteplanner.backend.shared.ratelimit.RateLimitService",
            "org.danteplanner.backend.shared.ratelimit.RateLimitStore");

    static final Set<String> STORE_HANDLE_HOLDERS = Set.of(
            "org.danteplanner.backend.planner.service.RedisViewRecorder",
            "org.danteplanner.backend.shared.config.ShedLockConfig",
            "org.danteplanner.backend.shared.freshness.MarkerStore",
            "org.danteplanner.backend.shared.freshness.RaiseBatcher",
            "org.danteplanner.backend.shared.gtid.GtidGateConfig",
            "org.danteplanner.backend.shared.sse.SseChannelSender",
            "org.danteplanner.backend.shared.sse.SseSubscriberConfig");

    private static final String IS_AVAILABLE = "isAvailable";

    private static final DescribedPredicate<JavaClass> STORE_HANDLES =
            equivalentTo(StringRedisTemplate.class)
                    .or(assignableTo(RedisConnectionFactory.class))
                    .or(equivalentTo(ProxyManager.class))
                    .or(equivalentTo(RedisClient.class));

    static final ArchRule ONLY_THE_GATE_READS_ROLE_HEALTH = onlyTheGateReadsRoleHealth(ROLE_HEALTH_READERS);

    static final ArchRule STORE_HANDLES_STAY_BEHIND_THE_GATE =
            storeHandlesStayBehindTheGate(STORE_DEFINITIONS, STORE_HANDLE_HOLDERS);

    private RedisRoleGateRule() {
    }

    static ArchRule onlyTheGateReadsRoleHealth(Set<String> frozen) {
        return noClasses()
                .that().doNotBelongToAnyOf(RoleGated.class)
                .and(not(declaredIn(frozen)))
                .should(accessRoleHealth())
                .as("only RoleGated asks whether a Redis role is available; a caller reaches the role's store"
                        + " through RoleGated.call");
    }

    static ArchRule storeHandlesStayBehindTheGate(Set<String> definitions, Set<String> frozen) {
        Set<String> exempt = new HashSet<>(definitions);
        exempt.addAll(frozen);
        return classes()
                .that().resideOutsideOfPackage("..shared.redis..")
                .and(not(declaredIn(exempt)))
                .should(holdNoStoreHandle())
                .as("outside shared.redis no class holds a Redis store handle (StringRedisTemplate,"
                        + " RedisConnectionFactory, ProxyManager, RedisClient) in a field, a constructor parameter"
                        + " or a @Bean parameter; it is handed a RoleGated instead");
    }

    private static ArchCondition<JavaClass> accessRoleHealth() {
        return new ArchCondition<>("call or reference RedisRoleHealth.isAvailable") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                    if (access.getTargetOwner().isEquivalentTo(RedisRoleHealth.class)
                            && access.getName().equals(IS_AVAILABLE)) {
                        events.add(SimpleConditionEvent.satisfied(javaClass, access.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> holdNoStoreHandle() {
        return new ArchCondition<>("hold no Redis store handle") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    if (STORE_HANDLES.test(field.getRawType())) {
                        events.add(SimpleConditionEvent.violated(javaClass, "Field <" + field.getFullName()
                                + "> holds <" + field.getRawType().getName() + ">"));
                    }
                }
                javaClass.getConstructors().forEach(constructor -> reportHandleParameters(constructor, events));
                javaClass.getMethods().stream()
                        .filter(method -> method.isAnnotatedWith(Bean.class))
                        .forEach(method -> reportHandleParameters(method, events));
            }
        };
    }

    private static void reportHandleParameters(JavaCodeUnit codeUnit, ConditionEvents events) {
        List<JavaClass> parameterTypes = codeUnit.getRawParameterTypes();
        for (int index = 0; index < parameterTypes.size(); index++) {
            JavaClass type = parameterTypes.get(index);
            if (STORE_HANDLES.test(type)) {
                events.add(SimpleConditionEvent.violated(codeUnit.getOwner(), "Parameter " + index + " of <"
                        + codeUnit.getFullName() + "> takes <" + type.getName() + ">"));
            }
        }
    }

    private static DescribedPredicate<JavaClass> declaredIn(Set<String> frozen) {
        return DescribedPredicate.describe("declared in " + frozen,
                javaClass -> frozen.contains(javaClass.getName().split("\\$", 2)[0]));
    }
}
