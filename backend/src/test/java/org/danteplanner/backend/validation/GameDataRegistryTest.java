package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.validation.GameDataLoader;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An unreadable game-data path is a startup failure, not a silently empty registry.
 *
 * <p>Every id lookup against an empty registry returns false, so the pod reports healthy while
 * rejecting 100% of planner saves with INVALID_ID_REFERENCE.</p>
 */
class GameDataRegistryTest {

    @Test
    @DisplayName("an unreadable data path fails startup instead of emptying the registry")
    void init_WhenDataPathUnreadable_ThrowsIllegalState() {
        GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(new ObjectMapper()));
        ReflectionTestUtils.setField(registry, "dataPath", "/nonexistent/game-data-path");

        assertThrows(IllegalStateException.class, registry::init);
    }
}
