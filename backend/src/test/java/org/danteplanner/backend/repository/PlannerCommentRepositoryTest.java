package org.danteplanner.backend.repository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;

import jakarta.persistence.EntityManager;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PlannerCommentRepository batch count query.
 *
 * <p>Tests countByPlannerIdsGrouped which is used by PlannerService
 * to batch-fetch comment counts for list views without N+1 queries.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class PlannerCommentRepositoryTest {

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Planner plannerA;
    private Planner plannerB;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.deleteAll();

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        plannerA = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
        plannerB = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
        entityManager.flush();
        entityManager.clear();
    }

    private PlannerComment saveComment(UUID plannerId) {
        PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "content", null, 0);
        PlannerComment saved = commentRepository.save(comment);
        entityManager.flush();
        return saved;
    }

    private Map<UUID, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (Long) row[1]
        ));
    }

    @Nested
    @DisplayName("countByPlannerIdsGrouped Tests")
    class CountByPlannerIdsGroupedTests {

        @Test
        @DisplayName("Returns empty list when plannerIds is empty")
        void countByPlannerIdsGrouped_WhenEmptyInput_ReturnsEmptyList() {
            List<Object[]> result = commentRepository.countByPlannerIdsGrouped(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns zero rows for planners with no comments")
        void countByPlannerIdsGrouped_WhenNoComments_ReturnsNoRows() {
            List<Object[]> result = commentRepository.countByPlannerIdsGrouped(
                    List.of(plannerA.getId(), plannerB.getId()));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns correct count for a single planner with comments")
        void countByPlannerIdsGrouped_WhenSinglePlanner_ReturnsCorrectCount() {
            saveComment(plannerA.getId());
            saveComment(plannerA.getId());
            saveComment(plannerA.getId());

            List<Object[]> result = commentRepository.countByPlannerIdsGrouped(List.of(plannerA.getId()));
            Map<UUID, Long> counts = toMap(result);

            assertEquals(1, counts.size());
            assertEquals(3L, counts.get(plannerA.getId()));
        }

        @Test
        @DisplayName("Returns counts grouped per planner when multiple planners have comments")
        void countByPlannerIdsGrouped_WhenMultiplePlanners_ReturnsCountsPerPlanner() {
            saveComment(plannerA.getId());
            saveComment(plannerA.getId());
            saveComment(plannerB.getId());

            List<Object[]> result = commentRepository.countByPlannerIdsGrouped(
                    List.of(plannerA.getId(), plannerB.getId()));
            Map<UUID, Long> counts = toMap(result);

            assertEquals(2L, counts.get(plannerA.getId()));
            assertEquals(1L, counts.get(plannerB.getId()));
        }

        @Test
        @DisplayName("Excludes soft-deleted comments from count")
        void countByPlannerIdsGrouped_WhenSoftDeletedComments_ExcludesThem() {
            PlannerComment live = saveComment(plannerA.getId());
            PlannerComment deleted = saveComment(plannerA.getId());

            deleted.softDelete();
            commentRepository.save(deleted);
            entityManager.flush();
            entityManager.clear();

            List<Object[]> result = commentRepository.countByPlannerIdsGrouped(List.of(plannerA.getId()));
            Map<UUID, Long> counts = toMap(result);

            assertEquals(1L, counts.get(plannerA.getId()));
        }

        @Test
        @DisplayName("Only returns rows for planners in the input list")
        void countByPlannerIdsGrouped_WhenScoped_ReturnsOnlyInputPlanners() {
            saveComment(plannerA.getId());
            saveComment(plannerB.getId());

            // Query only plannerA
            List<Object[]> result = commentRepository.countByPlannerIdsGrouped(List.of(plannerA.getId()));
            Map<UUID, Long> counts = toMap(result);

            assertEquals(1, counts.size());
            assertNotNull(counts.get(plannerA.getId()));
            assertNull(counts.get(plannerB.getId()));
        }
    }
}
