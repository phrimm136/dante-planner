package org.danteplanner.backend.entity;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code @JsonCreator} null contract for the two enums whose stored value differs from the
 * constant name.
 *
 * <p>Jackson invokes the creator with null for an absent or explicitly null field, so returning
 * null is what keeps such a property optional. Delegating the lookup to {@code EnumLookup} would
 * lose that: it reports an unrecognised value, and null is not a value the caller supplied.</p>
 */
class JsonCreatorNullContractTest {

    @Test
    void plannerStatus_WhenValueIsNull_ReturnsNullRatherThanThrowing() {
        assertThat(PlannerStatus.fromValue(null)).isNull();
    }

    @Test
    void authProviderType_WhenValueIsNull_ReturnsNullRatherThanThrowing() {
        assertThat(AuthProviderType.fromValue(null)).isNull();
    }

    @Test
    void plannerStatus_WhenValueIsStored_ResolvesByWireValueNotName() {
        assertThat(PlannerStatus.fromValue("draft")).isEqualTo(PlannerStatus.DRAFT);
        // The constant name is not a value this enum accepts; only the stored form is.
        assertThatThrownBy(() -> PlannerStatus.fromValue("DRAFT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown PlannerStatus value: DRAFT");
    }

    @Test
    void authProviderType_WhenValueIsStored_ResolvesByWireValueNotName() {
        assertThat(AuthProviderType.fromValue("google")).isEqualTo(AuthProviderType.GOOGLE);
        assertThatThrownBy(() -> AuthProviderType.fromValue("GOOGLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown AuthProviderType value: GOOGLE");
    }
}
