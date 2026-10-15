package org.danteplanner.backend.shared.freshness;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessDeclarationsTest {

    @AfterEach
    void tearDown() {
        FreshnessDeclarations.clear();
    }

    @Test
    void declare_WhenNoRequestWindowIsOpen_DeclaresNothing() {
        FreshnessDeclarations.declare(new FreshnessScope.PublicCatalog());

        assertThat(FreshnessDeclarations.declared()).isEmpty();
    }

    @Test
    void declare_WhenCalledTwiceInOneWindow_HoldsEachScopeOnce() {
        FreshnessScope entity = new FreshnessScope.Entity(UUID.randomUUID());
        FreshnessDeclarations.begin();

        FreshnessDeclarations.declare(entity, new FreshnessScope.PublicCatalog());
        FreshnessDeclarations.declare(new FreshnessScope.PublicCatalog(), new FreshnessScope.OwnerList(7L));

        assertThat(FreshnessDeclarations.declared()).containsExactlyInAnyOrder(
                entity, new FreshnessScope.PublicCatalog(), new FreshnessScope.OwnerList(7L));
    }

    @Test
    void begin_WhenAPreviousWindowDeclaredScopes_StartsEmpty() {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declare(new FreshnessScope.PublicCatalog());

        FreshnessDeclarations.begin();

        assertThat(FreshnessDeclarations.declared()).isEmpty();
    }
}
