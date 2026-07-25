package org.danteplanner.backend.moderation;

import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.ModerationAuditService;
import org.danteplanner.backend.moderation.service.ModerationService;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every moderator action leaves an audit record, and every declared action type has a producer.
 *
 * <p>The completeness case derives its axis from {@link ModerationAction.ActionType#values()}, so a
 * constant added without a case here fails rather than joining the enum unused. That is how
 * {@code PROMOTE} and {@code DEMOTE} went unwritten: they were declared and never reached.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationAuditMatrixTest {

    @Mock UserRepository userRepository;
    @Mock PlannerRepository plannerRepository;
    @Mock PlannerCommentRepository plannerCommentRepository;
    @Mock PlannerStatsRepository plannerStatsRepository;
    @Mock PlannerCatalogService plannerCatalogService;
    @Mock ModerationActionRepository moderationActionRepository;
    ModerationAuditService auditService;
    @Mock SsePublisher ssePublisher;
    @Mock TokenBlacklistService tokenBlacklistService;

    private ModerationService moderationService;
    private AdminService adminService;

    private static final Long ACTOR = 1L;
    private static final Long TARGET = 2L;
    private final UUID plannerId = UUID.randomUUID();

    /** Each entry drives one operation and names the record it must leave behind. */
    private Map<ModerationAction.ActionType, Runnable> auditProducers() {
        Map<ModerationAction.ActionType, Runnable> producers = new LinkedHashMap<>();
        producers.put(ModerationAction.ActionType.TIMEOUT,
                () -> moderationService.timeoutUser(ACTOR, TARGET, 60, "spam"));
        producers.put(ModerationAction.ActionType.CLEAR_TIMEOUT,
                () -> moderationService.removeTimeout(ACTOR, TARGET, "appealed"));
        producers.put(ModerationAction.ActionType.BAN,
                () -> moderationService.banUser(ACTOR, TARGET, "repeat offender"));
        producers.put(ModerationAction.ActionType.UNBAN,
                () -> moderationService.unbanUser(ACTOR, TARGET, "appealed"));
        producers.put(ModerationAction.ActionType.DELETE_PLANNER,
                () -> moderationService.deletePlanner(ACTOR, plannerId, "off topic"));
        producers.put(ModerationAction.ActionType.UNPUBLISH_PLANNER,
                () -> moderationService.unpublishPlanner(ACTOR, plannerId));
        producers.put(ModerationAction.ActionType.DELETE_COMMENT,
                () -> moderationService.deleteComment(ACTOR, 10L));
        producers.put(ModerationAction.ActionType.HIDE_FROM_RECOMMENDED,
                () -> moderationService.hideFromRecommended(plannerId, ACTOR, new HidePlannerRequest("misleading")));
        producers.put(ModerationAction.ActionType.UNHIDE_FROM_RECOMMENDED,
                () -> moderationService.unhideFromRecommended(plannerId, ACTOR));
        producers.put(ModerationAction.ActionType.PROMOTE,
                () -> adminService.changeRole(ACTOR, TARGET, UserRole.MODERATOR));
        producers.put(ModerationAction.ActionType.DEMOTE,
                () -> adminService.changeRole(ACTOR, TARGET, UserRole.NORMAL));
        return producers;
    }

    @BeforeEach
    void setUp() {
        auditService = new ModerationAuditService(moderationActionRepository);
        moderationService = new ModerationService(userRepository, plannerRepository,
                plannerCommentRepository, plannerStatsRepository, plannerCatalogService,
                moderationActionRepository, auditService, ssePublisher);
        adminService = new AdminService(userRepository, tokenBlacklistService, auditService);

        User actor = user(ACTOR, UserRole.ADMIN);
        User target = user(TARGET, UserRole.MODERATOR);
        when(userRepository.findByIdAndDeletedAtIsNull(ACTOR)).thenReturn(Optional.of(actor));
        when(userRepository.findByIdAndDeletedAtIsNull(TARGET)).thenReturn(Optional.of(target));
        when(userRepository.findWithLockByIdAndDeletedAtIsNull(ACTOR)).thenReturn(Optional.of(actor));
        when(userRepository.findWithLockByIdAndDeletedAtIsNull(TARGET)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.countByRole(any())).thenReturn(5L);

        Planner planner = plannerAggregate(actor);
        when(plannerRepository.findAggregate(plannerId)).thenReturn(Optional.of(planner));
        when(plannerRepository.save(any(Planner.class))).thenAnswer(i -> i.getArgument(0));
        when(plannerStatsRepository.findById(any())).thenReturn(Optional.empty());

        PlannerComment comment = new PlannerComment(plannerId, TARGET, "text", null, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(comment, "publicId", UUID.randomUUID());
        when(plannerCommentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(plannerCommentRepository.save(any(PlannerComment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @TestFactory
    @DisplayName("every moderator action leaves an audit record of the matching type")
    Stream<DynamicTest> each_action_writes_its_audit_record() {
        return auditProducers().entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey().name(),
                () -> {
                    org.mockito.Mockito.clearInvocations(moderationActionRepository);
                    entry.getValue().run();

                    ArgumentCaptor<ModerationAction> captor = ArgumentCaptor.forClass(ModerationAction.class);
                    verify(moderationActionRepository, atLeastOnce()).save(captor.capture());
                    assertTrue(captor.getAllValues().stream()
                                    .anyMatch(a -> a.getActionType() == entry.getKey()),
                            entry.getKey() + " left no audit record of its own type");
                }));
    }

    @Test
    @DisplayName("no declared action type is left without a producer")
    void every_action_type_has_a_producer() {
        Set<ModerationAction.ActionType> covered = auditProducers().keySet();
        Set<ModerationAction.ActionType> declared = EnumSet.allOf(ModerationAction.ActionType.class);

        assertEquals(declared, EnumSet.copyOf(covered),
                "an action type with no producer is either an unaudited action or dead weight");
    }

    private User user(Long id, UserRole role) {
        return User.builder()
                .id(id)
                .email("u" + id + "@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + id)
                .usernameEpithet("TEST")
                .usernameSuffix("u" + id)
                .publicId(UUID.randomUUID())
                .role(role)
                .build();
    }

    private Planner plannerAggregate(User owner) {
        Planner planner = Planner.builder()
                .id(plannerId)
                .user(owner)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .build();
        planner.attach(
                PlannerContent.builder().title("t").category("5F").content("{}").build(),
                PlannerPublication.builder().build(),
                PlannerModeration.builder().build());
        return planner;
    }
}
