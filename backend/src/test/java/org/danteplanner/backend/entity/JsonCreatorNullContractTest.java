package org.danteplanner.backend.entity;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertNull(PlannerStatus.fromValue(null));
    }

    @Test
    void authProviderType_WhenValueIsNull_ReturnsNullRatherThanThrowing() {
        assertNull(AuthProviderType.fromValue(null));
    }

    @Test
    void plannerStatus_WhenValueIsStored_ResolvesByWireValueNotName() {
        assertEquals(PlannerStatus.DRAFT, PlannerStatus.fromValue("draft"));
        // The constant name is not a value this enum accepts; only the stored form is.
        assertThrows(IllegalArgumentException.class, () -> PlannerStatus.fromValue("DRAFT"));
    }

    @Test
    void authProviderType_WhenValueIsStored_ResolvesByWireValueNotName() {
        assertEquals(AuthProviderType.GOOGLE, AuthProviderType.fromValue("google"));
        assertThrows(IllegalArgumentException.class, () -> AuthProviderType.fromValue("GOOGLE"));
    }
}
