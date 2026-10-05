package org.danteplanner.backend.moderation;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.api.ModerationTargetType;
import org.danteplanner.backend.integration.ExclusiveDatabaseSupport;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.ModerationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
class ModerationAuditTrailBoundIT extends ExclusiveDatabaseSupport {

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
                    .actionType(ModerationActionType.TIMEOUT)
                    .targetType(ModerationTargetType.USER)
                    .reason("seed " + i)
                    .build());
        }
    }

    @Test
    void auditTrailPage_WhenRead_IsTruncatedByTheQuery() {
        List<ModerationAction> page = moderationQueryService.getModerationActions();

        assertTrue(page.size() < SEEDED_RECORDS,
                "the whole table came back: the limit is not being applied by the query");
        assertEquals(100, page.size());
    }

    @Test
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
