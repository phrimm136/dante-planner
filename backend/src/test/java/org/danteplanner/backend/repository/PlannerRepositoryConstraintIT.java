package org.danteplanner.backend.repository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.StoredPlannerContent;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;

import org.danteplanner.backend.user.api.AuthProviderType;
import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Constraint validation tests for Planner-related entities.
 *
 * <p>Tests database constraint enforcement (FK, UNIQUE, NOT NULL) at the persistence layer.
 * Uses entityManager.flush() to trigger constraint checks immediately.
 * Separate from business logic tests - focuses on database integrity.</p>
 */
@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerRepositoryConstraintIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
    }

    private Planner buildPlanner(Long ownerId, String title) {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .build();
        planner.attach(
                StoredPlannerContent.holding(PlannerContent.builder()
                        .title(title)
                        .category("5F")
                        .gameContentVersion(1)
                        .build(), "{}"),
                PlannerPublication.builder().build(),
                PlannerModeration.builder().build());
        return planner;
    }

    private void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    @Nested
    class ForeignKeyTests {
        @Test
        void foreignKey_WhenInvalidPlannerId_ThrowsException() {
            assertThatThrownBy(() -> {
                PlannerVote vote = new PlannerVote(testUser.getId(), UUID.randomUUID(), VoteType.UP);
                voteRepository.saveAndFlush(vote);
            }).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_vote_planner");
        }
        @Test
        void foreignKey_WhenInvalidUserId_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            assertThatThrownBy(() -> {
                PlannerVote vote = new PlannerVote(99999L, planner.getId(), VoteType.UP);
                voteRepository.saveAndFlush(vote);
            }).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_vote_user");
        }
        @Test
        void foreignKey_WhenInvalidPlannerIdInComment_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                PlannerComment comment = new PlannerComment(
                        UUID.randomUUID(),
                        testUser.getId(),
                        "Test comment",
                        null,
                        0
                );
                commentRepository.save(comment);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_comment_planner");
        }
        @Test
        void foreignKey_WhenInvalidUserIdInComment_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            assertThatThrownBy(() -> inTransaction(() -> {
                PlannerComment comment = new PlannerComment(
                        planner.getId(),
                        99999L,
                        "Test comment",
                        null,
                        0
                );
                commentRepository.save(comment);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_comment_user");
        }
        @Test
        void foreignKey_WhenCascadeDelete_DeletesChildVotes() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerVote vote = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
            voteRepository.save(vote);

            long votesBeforeDelete = countVotesFor(planner.getId());
            assertThat(votesBeforeDelete).isEqualTo(1);

            plannerRepository.delete(planner);

            long votesAfterDelete = countVotesFor(planner.getId());
            assertThat(votesAfterDelete).isZero();
        }
        @Test
        void foreignKey_WhenCascadeDelete_DeletesChildComments() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerComment comment = new PlannerComment(
                    planner.getId(),
                    testUser.getId(),
                    "Test comment",
                    null,
                    0
            );
            commentRepository.save(comment);

            long commentsBeforeDelete = countCommentsFor(planner.getId());
            assertThat(commentsBeforeDelete).isEqualTo(1);

            plannerRepository.delete(planner);

            long commentsAfterDelete = countCommentsFor(planner.getId());
            assertThat(commentsAfterDelete).isZero();
        }

        private long countVotesFor(UUID plannerId) {
            return voteRepository.findAll().stream()
                    .filter(vote -> plannerId.equals(vote.getPlannerId()))
                    .count();
        }

        private long countCommentsFor(UUID plannerId) {
            return commentRepository.findAll().stream()
                    .filter(comment -> plannerId.equals(comment.getPlannerId()))
                    .count();
        }
    }

    @Nested
    class UniqueConstraintTests {

        @Test
        void uniqueConstraint_WhenDuplicateVote_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
            voteRepository.save(vote1);

            assertThatThrownBy(() -> inTransaction(() -> {
                PlannerVote vote2 = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
                voteRepository.save(vote2);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void uniqueConstraint_WhenDifferentUserSamePlanner_Allowed() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
            User otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
            PlannerVote vote2 = new PlannerVote(otherUser.getId(), planner.getId(), VoteType.UP);

            assertThatNoException().isThrownBy(() -> inTransaction(() -> {
                voteRepository.save(vote1);
                voteRepository.save(vote2);
                entityManager.flush();
            }));
        }

        @Test
        void uniqueConstraint_WhenSameUserDifferentPlanner_Allowed() {
            Planner planner1 = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
            Planner planner2 = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner1.getId(), VoteType.UP);
            PlannerVote vote2 = new PlannerVote(testUser.getId(), planner2.getId(), VoteType.UP);

            assertThatNoException().isThrownBy(() -> inTransaction(() -> {
                voteRepository.save(vote1);
                voteRepository.save(vote2);
                entityManager.flush();
            }));
        }

        @Test
        void uniqueConstraint_WhenDuplicateUsernameSuffix_ThrowsException() {
            String suffix = TestDataFactory.uniqueSuffix("");

            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("google-" + UUID.randomUUID())
                    .usernameEpithet("TEST")
                    .usernameSuffix(suffix)
                    .role(UserRole.NORMAL)
                    .build();
            userRepository.save(user1);

            assertThatThrownBy(() -> inTransaction(() -> {
                User user2 = User.builder()
                        .email("user2@example.com")
                        .provider(AuthProviderType.GOOGLE)
                        .providerId("google-" + UUID.randomUUID())
                        .usernameEpithet("TEST")
                        .usernameSuffix(suffix)
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user2);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_users_username_suffix");
        }

        @Test
        void uniqueConstraint_WhenDuplicateProviderAndProviderId_ThrowsException() {
            String providerId = "google-" + UUID.randomUUID();
            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId(providerId)
                    .usernameEpithet("TEST")
                    .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                    .role(UserRole.NORMAL)
                    .build();
            userRepository.save(user1);

            assertThatThrownBy(() -> inTransaction(() -> {
                User user2 = User.builder()
                        .email("user2@example.com")
                        .provider(AuthProviderType.GOOGLE)
                        .providerId(providerId)
                        .usernameEpithet("TEST")
                        .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user2);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_provider_provider_id");
        }

        @Test
        void uniqueConstraint_WhenDifferentProviderSameProviderId_Allowed() {
            String providerId = "shared-" + UUID.randomUUID();
            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId(providerId)
                    .usernameEpithet("TEST")
                    .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                    .role(UserRole.NORMAL)
                    .build();
            User user2 = User.builder()
                    .email("user2@example.com")
                    .provider(AuthProviderType.APPLE)
                    .providerId(providerId)
                    .usernameEpithet("TEST")
                    .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                    .role(UserRole.NORMAL)
                    .build();

            assertThatNoException().isThrownBy(() -> inTransaction(() -> {
                userRepository.save(user1);
                userRepository.save(user2);
                entityManager.flush();
            }));
        }
    }

    @Nested
    class NotNullTests {

        @Test
        void notNullConstraint_WhenMissingTitle_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                Planner planner = buildPlanner(testUser.getId(), null);
                plannerRepository.save(planner);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void notNullConstraint_WhenMissingOwner_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                Planner planner = buildPlanner(null, "Test Title");
                plannerRepository.save(planner);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void notNullConstraint_WhenMissingCommentContent_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            assertThatThrownBy(() -> inTransaction(() -> {
                PlannerComment comment = new PlannerComment(
                        planner.getId(),
                        testUser.getId(),
                        null,
                        null,
                        0
                );
                commentRepository.save(comment);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'content' cannot be null");
        }

        @Test
        void notNullConstraint_WhenMissingEmail_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                User user = User.builder()
                        .email(null)
                        .provider(AuthProviderType.GOOGLE)
                        .providerId("google-123")
                        .usernameEpithet("TEST")
                        .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'email' cannot be null");
        }

        @Test
        void notNullConstraint_WhenMissingProvider_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                User user = User.builder()
                        .email("test@example.com")
                        .provider(null)
                        .providerId("google-123")
                        .usernameEpithet("TEST")
                        .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'provider' cannot be null");
        }

        @Test
        void notNullConstraint_WhenMissingProviderId_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                User user = User.builder()
                        .email("test@example.com")
                        .provider(AuthProviderType.GOOGLE)
                        .providerId(null)
                        .usernameEpithet("TEST")
                        .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'provider_id' cannot be null");
        }

        @Test
        void notNullConstraint_WhenEmptyStringAllowed_NullForbidden() {
            Planner planner1 = buildPlanner(testUser.getId(), "");
            assertThatNoException().isThrownBy(() -> inTransaction(() -> {
                plannerRepository.save(planner1);
                entityManager.flush();
            }));

            assertThatThrownBy(() -> inTransaction(() -> {
                Planner planner2 = buildPlanner(testUser.getId(), null);
                plannerRepository.save(planner2);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void notNullConstraint_WhenDefaultValueApplied_PublishedFalse() {
            Planner planner = buildPlanner(testUser.getId(), "Test");
            plannerRepository.save(planner);

            Planner saved = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(saved.isPublished()).isFalse();
        }

        @Test
        void notNullConstraint_WhenDefaultValueApplied_UpvotesZero() {
            Planner planner = buildPlanner(testUser.getId(), "Test");
            plannerRepository.save(planner);
            statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());

            PlannerStats saved = statsRepository.findById(planner.getId()).orElseThrow();
            assertThat(saved.getUpvotes()).isZero();
        }

        @Test
        void notNullConstraint_WhenDefaultValueApplied_CommentUpvoteCountZero() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerComment comment = new PlannerComment(
                    planner.getId(),
                    testUser.getId(),
                    "Test content",
                    null,
                    0
            );
            commentRepository.save(comment);

            PlannerComment saved = commentRepository.findById(comment.getId()).orElseThrow();
            assertThat(saved.getUpvoteCount()).isZero();
        }
    }
}
