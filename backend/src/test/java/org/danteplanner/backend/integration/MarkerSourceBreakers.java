package org.danteplanner.backend.integration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.support.Hang;

import static org.assertj.core.api.Assertions.assertThat;

final class MarkerSourceBreakers {

    private static final List<RedisRole> ROLES = List.of(RedisRole.AUTH_LOCAL, RedisRole.AUTH);

    private MarkerSourceBreakers() {
    }

    static void awaitClosed(RedisRoleHealth health) {
        Hang.until(() -> allAvailable(health, true),
                "the Redis role probe closing both marker-source role breakers after the auth Redis cut");
    }

    static void awaitOpen(RedisRoleHealth health) {
        Hang.until(() -> allAvailable(health, false),
                "the Redis role probe opening both marker-source role breakers during the auth Redis cut");
    }

    static void assertClosed(RedisRoleHealth health, String because) {
        assertThat(availability(health))
                .as(because)
                .containsExactlyInAnyOrderEntriesOf(ROLES.stream()
                        .collect(Collectors.toMap(role -> role, role -> true)));
    }

    private static boolean allAvailable(RedisRoleHealth health, boolean available) {
        return availability(health).values().stream().allMatch(value -> value == available);
    }

    private static Map<RedisRole, Boolean> availability(RedisRoleHealth health) {
        return ROLES.stream().collect(Collectors.toMap(role -> role, health::isAvailable));
    }
}
