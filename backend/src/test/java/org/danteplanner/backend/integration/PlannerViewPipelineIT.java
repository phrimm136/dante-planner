package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * View pipeline seam: the flush dedups on the (planner, viewer, day) composite key, so
 * recording the same view twice in one window persists one row and increments the counter
 * once, and replaying an already-flushed batch increments nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerViewPipelineIT extends SharedMySqlContainerSupport {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 15);
    private static final String VIEWER = "viewer-hash-abc";

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_view_pipeline_it");
    }

    @Autowired
    private PlannerViewRecorder recorder;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerViewRepository plannerViewRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        plannerViewRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        User owner = TestDataFactory.createTestUser(userRepository, "viewer-owner@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        plannerId = planner.getId();
    }

    private int viewCount() {
        return plannerRepository.findById(plannerId).orElseThrow().getViewCount();
    }

    @Test
    @DisplayName("view_WhenSameViewerDay_Noops")
    void view_WhenSameViewerDay_Noops() {
        recorder.record(plannerId, VIEWER, DAY);
        recorder.record(plannerId, VIEWER, DAY);
        recorder.flush();

        assertThat(viewCount())
                .as("a repeated view in one window increments the counter once")
                .isEqualTo(1);
        assertThat(plannerViewRepository.count())
                .as("a repeated view in one window persists a single view row")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("viewFlush_WhenReplayed_NoDoubleCount")
    void viewFlush_WhenReplayed_NoDoubleCount() {
        recorder.record(plannerId, VIEWER, DAY);
        recorder.flush();
        int afterFirst = viewCount();

        recorder.record(plannerId, VIEWER, DAY);
        recorder.flush();

        assertThat(viewCount())
                .as("replaying an already-flushed view must not double-count")
                .isEqualTo(afterFirst);
    }
}
