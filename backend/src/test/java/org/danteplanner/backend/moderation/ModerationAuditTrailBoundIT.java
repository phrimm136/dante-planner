package org.danteplanner.backend.moderation;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.ModerationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.danteplanner.backend.user.repository.UserRepository;

/**
 * The audit trail the dashboard reads is bounded and ordered by the database, not by the heap.
 *
 * <p>Seeds more records than the dashboard returns, so a page that came back whole — or came back
 * from the wrong end — is visible in the assertions rather than only in the query plan.</p>
 */
@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ModerationAuditTrailBoundIT {

    /**
     * The subject is the whole audit trail: the assertions compare a bounded page against the
     * newest record in the table, and there is no id to narrow either side to. A neighbour
     * moderating anything between the two reads puts a newer record outside the page.
     */
    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "audit_trail_bound");
    }


    /** Comfortably more than the dashboard's page, so truncation has something to remove. */
    private static final int SEEDED_RECORDS = 150;

    @Autowired
    private ModerationQueryService moderationQueryService;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // A real actor row: actor_id carries a foreign key, so a literal id only works on a
        // database that does not enforce it.
        Long actorId = TestDataFactory.createTestUser(userRepository, "audit-actor@example.com").getId();

        for (int i = 0; i < SEEDED_RECORDS; i++) {
            moderationActionRepository.save(ModerationAction.builder()
                    .actorId(actorId)
                    .targetUuid(UUID.randomUUID().toString())
                    .actionType(ModerationAction.ActionType.TIMEOUT)
                    .targetType(ModerationAction.TargetType.USER)
                    .reason("seed " + i)
                    .build());
        }
    }

    @Test
    @DisplayName("the dashboard reads a bounded page, not the whole table")
    void auditTrailPage_WhenRead_IsTruncatedByTheQuery() {
        List<ModerationAction> page = moderationQueryService.getModerationActions();

        assertTrue(page.size() < SEEDED_RECORDS,
                "the whole table came back: the limit is not being applied by the query");
        assertEquals(100, page.size());
    }

    @Test
    @DisplayName("the bounded page is the newest end of the trail, newest first")
    void auditTrailPage_WhenRead_HoldsNewestRecordsFirst() {
        List<ModerationAction> page = moderationQueryService.getModerationActions();

        assertTrue(isNonIncreasing(page), "records are not ordered newest first");

        Instant newestStored = moderationActionRepository.findAll().stream()
                .map(ModerationAction::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        assertEquals(newestStored, page.get(0).getCreatedAt(),
                "the page starts at the oldest end of the trail");
    }

    private static boolean isNonIncreasing(List<ModerationAction> actions) {
        for (int i = 1; i < actions.size(); i++) {
            if (actions.get(i).getCreatedAt().isAfter(actions.get(i - 1).getCreatedAt())) {
                return false;
            }
        }
        return true;
    }
}
