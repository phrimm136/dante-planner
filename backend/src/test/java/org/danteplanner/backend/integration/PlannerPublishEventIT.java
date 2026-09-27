package org.danteplanner.backend.integration;

import java.util.UUID;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Publish-event seam: the publish SSE broadcast must be emitted only after the publishing
 * transaction commits, so a transaction that rolls back after the old broadcast point emits
 * no SSE event at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerPublishEventIT extends SharedMySqlContainerSupport {


    @Autowired
    private PlannerPublishingService plannerPublishingService;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SseService sseService;

    private User owner;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        Mockito.reset(sseService);
        owner = TestDataFactory.createTestUser(userRepository, "publisher@example.com");
        plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, false).getId();
    }

    @Test
    @Transactional
    void publish_WhenRolledBack_EmitsNoSseEvent() {
        plannerPublishingService.publish(owner.getId(), plannerId);

        // The surrounding @Transactional rolls back, so a commit-only emission never fires.
        verify(sseService, never()).broadcastToAll(any(), any(), any());
    }
}
