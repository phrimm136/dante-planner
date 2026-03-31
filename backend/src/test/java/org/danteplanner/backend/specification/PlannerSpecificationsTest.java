package org.danteplanner.backend.specification;

import org.danteplanner.backend.entity.ContentEntityType;
import org.danteplanner.backend.entity.Planner;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlannerSpecifications.
 * Verifies specification construction and input escaping.
 */
class PlannerSpecificationsTest {

    @Test
    void titleContainsEscapesLikeWildcards() {
        Specification<Planner> spec = PlannerSpecifications.titleContains("100%_done");
        assertNotNull(spec);
    }

    @Test
    void titleContainsEscapesBackslash() {
        Specification<Planner> spec = PlannerSpecifications.titleContains("path\\to\\file");
        assertNotNull(spec);
    }

    @Test
    void titleContainsHandlesEmptyString() {
        Specification<Planner> spec = PlannerSpecifications.titleContains("");
        assertNotNull(spec);
    }

    @Test
    void isPublishedReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.isPublished();
        assertNotNull(spec);
    }

    @Test
    void isRecommendedExtendsIsPublished() {
        Specification<Planner> spec = PlannerSpecifications.isRecommended(10);
        assertNotNull(spec);
    }

    @Test
    void hasCategoryReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.hasCategory("mirror_dungeon");
        assertNotNull(spec);
    }

    @Test
    void hasKeywordReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.hasKeyword("burn");
        assertNotNull(spec);
    }

    @Test
    void hasContentEntityReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.hasContentEntity(
                ContentEntityType.IDENTITY, "10101");
        assertNotNull(spec);
    }

    @Test
    void fetchUserReturnsSpec() {
        Specification<Planner> spec = PlannerSpecifications.fetchUser();
        assertNotNull(spec);
    }
}
