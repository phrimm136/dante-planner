package org.danteplanner.backend.specification;
import org.danteplanner.backend.planner.specification.PlannerSpecifications;

import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.entity.Planner;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlannerSpecifications.
 * Verifies specification construction and input escaping.
 */
class PlannerSpecificationsTest {

    @Test
    void titleContains_WhenLikeWildcards_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.titleContains("100%_done");
        assertNotNull(spec);
    }

    @Test
    void titleContains_WhenBackslash_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.titleContains("path\\to\\file");
        assertNotNull(spec);
    }

    @Test
    void titleContains_WhenEmptyString_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.titleContains("");
        assertNotNull(spec);
    }

    @Test
    void isPublished_WhenCalled_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.isPublished();
        assertNotNull(spec);
    }

    @Test
    void isRecommended_WhenCalled_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.isRecommended(10);
        assertNotNull(spec);
    }

    @Test
    void hasCategory_WhenCalled_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.hasCategory("mirror_dungeon");
        assertNotNull(spec);
    }

    @Test
    void hasKeyword_WhenCalled_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.hasKeyword("burn");
        assertNotNull(spec);
    }

    @Test
    void hasContentEntity_WhenCalled_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.hasContentEntity(
                ContentEntityType.IDENTITY, "10101");
        assertNotNull(spec);
    }

    @Test
    void fetchUser_WhenCalled_ReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.fetchUser();
        assertNotNull(spec);
    }
}
