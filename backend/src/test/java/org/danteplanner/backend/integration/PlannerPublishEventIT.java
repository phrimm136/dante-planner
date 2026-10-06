package org.danteplanner.backend.integration;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.support.SseTopicRecorder;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    @Qualifier("sseLocalRedisConnectionFactory")
    private RedisConnectionFactory sseConnectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User owner;
    private UUID plannerId;
    private SseTopicRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = SseTopicRecorder.open(sseConnectionFactory, objectMapper);
        owner = TestDataFactory.createTestUser(userRepository, "publisher@example.com");
        plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, false).getId();
    }

    @AfterEach
    void closeRecorder() {
        recorder.close();
    }

    @Test
    void publish_WhenRolledBack_EmitsNoSseEvent() {
        transactionTemplate.executeWithoutResult(status -> {
            plannerPublishingService.publish(owner.getId(), plannerId);
            status.setRollbackOnly();
        });

        recorder.fence();
        assertThat(recorder.broadcasts(envelope -> envelope.payload() instanceof Map<?, ?> map
                && plannerId.toString().equals(map.get("plannerId"))))
                .isEmpty();
    }
}
