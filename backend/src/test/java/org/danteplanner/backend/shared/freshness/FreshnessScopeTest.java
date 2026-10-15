package org.danteplanner.backend.shared.freshness;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessScopeTest {

    @Test
    void key_WhenTheScopeIsOnePlanner_IsTheChangeKeyOfThatPlanner() {
        UUID plannerId = UUID.fromString("0b9c7e4e-2f6a-4f7e-9d3c-1a2b3c4d5e6f");

        assertThat(new FreshnessScope.Entity(plannerId).key())
                .isEqualTo("chg:planner:0b9c7e4e-2f6a-4f7e-9d3c-1a2b3c4d5e6f");
    }

    @Test
    void key_WhenTheScopeIsAnOwnersList_IsTheChangeKeyOfThatOwner() {
        assertThat(new FreshnessScope.OwnerList(42L).key()).isEqualTo("chg:owner:42");
    }

    @Test
    void key_WhenTheScopeIsAnAuthor_IsTheChangeKeyOfThatAuthor() {
        assertThat(new FreshnessScope.Author(42L).key()).isEqualTo("chg:author:42");
    }

    @Test
    void key_WhenTheScopeIsThePublicCatalog_IsTheCatalogChangeKey() {
        assertThat(new FreshnessScope.PublicCatalog().key()).isEqualTo("chg:catalog");
    }
}
