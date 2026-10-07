package org.danteplanner.backend.moderation;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.service.CommentModerationService;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.moderation.service.UserModerationService;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every moderator action leaves an audit record, and every declared action type has a producer.
 *
 * <p>The completeness case derives its axis from {@link ModerationActionType#values()}, so a
 * constant added without a case here fails rather than joining the enum unused. That is how
 * {@code PROMOTE} and {@code DEMOTE} went unwritten: they were declared and never reached.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ModerationAuditMatrixIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserModerationService userModerationService;

    @Autowired
    private PlannerModerationService plannerModerationService;

    @Autowired
    private CommentModerationService commentModerationService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Scene(User actor, User moderator, User member, UUID plannerId, Long commentId) {
    }

    /** Each entry drives one operation and names the record it must leave behind. */
    private Map<ModerationActionType, Consumer<Scene>> auditProducers() {
        Map<ModerationActionType, Consumer<Scene>> producers = new LinkedHashMap<>();
        producers.put(ModerationActionType.TIMEOUT, scene ->
                userModerationService.timeoutUser(scene.actor().getId(), scene.moderator().getId(), 60, "spam"));
        producers.put(ModerationActionType.CLEAR_TIMEOUT, scene ->
                userModerationService.removeTimeout(scene.actor().getId(), scene.moderator().getId(), "appealed"));
        producers.put(ModerationActionType.BAN, scene ->
                userModerationService.banUser(scene.actor().getId(), scene.moderator().getId(), "repeat offender"));
        producers.put(ModerationActionType.UNBAN, scene ->
                userModerationService.unbanUser(scene.actor().getId(), scene.moderator().getId(), "appealed"));
        producers.put(ModerationActionType.DELETE_PLANNER, scene ->
                plannerModerationService.deletePlanner(scene.actor().getId(), scene.plannerId(), "off topic"));
        producers.put(ModerationActionType.UNPUBLISH_PLANNER, scene ->
                plannerModerationService.unpublishPlanner(scene.actor().getId(), scene.plannerId()));
        producers.put(ModerationActionType.DELETE_COMMENT, scene ->
                commentModerationService.deleteComment(scene.actor().getId(), scene.commentId()));
        producers.put(ModerationActionType.HIDE_FROM_RECOMMENDED, scene ->
                plannerModerationService.hideFromRecommended(scene.plannerId(), scene.actor().getId(),
                        new HidePlannerRequest("misleading")));
        producers.put(ModerationActionType.UNHIDE_FROM_RECOMMENDED, scene ->
                plannerModerationService.unhideFromRecommended(scene.plannerId(), scene.actor().getId()));
        producers.put(ModerationActionType.PROMOTE, scene ->
                adminService.changeRole(scene.actor().getId(), scene.member().getId(), UserRole.MODERATOR));
        producers.put(ModerationActionType.DEMOTE, scene ->
                adminService.changeRole(scene.actor().getId(), scene.moderator().getId(), UserRole.NORMAL));
        return producers;
    }

    private Scene freshScene() {
        User actor = TestDataFactory.createAdmin(userRepository, "audit-matrix-admin@example.com");
        User moderator = TestDataFactory.createModerator(userRepository, "audit-matrix-mod@example.com");
        User member = TestDataFactory.createTestUser(userRepository, "audit-matrix-member@example.com");
        UUID plannerId = TestDataFactory.createTestPlanner(plannerRepository, member, true).getId();
        PlannerComment comment = commentRepository.save(new PlannerComment(plannerId, member.getId(), "text", null, 0));
        return new Scene(actor, moderator, member, plannerId, comment.getId());
    }

    private List<String> auditedActionsOf(User actor) {
        return jdbcTemplate.queryForList(
                "SELECT action_type FROM moderation_actions WHERE actor_id = ?", String.class, actor.getId());
    }

    @TestFactory
    Stream<DynamicTest> auditRecord_WhenActionPerformed_IsWritten() {
        return auditProducers().entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey().name(),
                () -> {
                    Scene scene = freshScene();

                    entry.getValue().accept(scene);

                    assertThat(auditedActionsOf(scene.actor()))
                            .as(entry.getKey() + " left no audit record of its own type")
                            .contains(entry.getKey().name());
                }));
    }

    @Test
    void actionType_WhenEnumerated_HasAProducer() {
        Set<ModerationActionType> covered = auditProducers().keySet();
        Set<ModerationActionType> declared = EnumSet.allOf(ModerationActionType.class);

        assertThat(EnumSet.copyOf(covered))
                .as("an action type with no producer is either an unaudited action or dead weight")
                .isEqualTo(declared);
    }
}
