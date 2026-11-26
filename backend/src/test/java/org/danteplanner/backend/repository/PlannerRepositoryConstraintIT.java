package org.danteplanner.backend.repository;
import org.danteplanner.backend.support.PlannerRows;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentContentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;

import org.danteplanner.backend.user.api.AuthProviderType;
import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.support.PlannerSeeds;
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
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private PlannerCommentContentRepository contentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
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
            StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);

            assertThatThrownBy(() -> {
                PlannerVote vote = new PlannerVote(99999L, planner.id(), VoteType.UP);
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
            StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);

            assertThatThrownBy(() -> inTransaction(() -> {
                PlannerComment comment = new PlannerComment(
                        planner.id(),
                        99999L,
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
            StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);

            PlannerVote vote = new PlannerVote(testUser.getId(), planner.id(), VoteType.UP);
            voteRepository.save(vote);

            long votesBeforeDelete = countVotesFor(planner.id());
            assertThat(votesBeforeDelete).isEqualTo(1);

            plannerRepository.deleteById(planner.id());

            long votesAfterDelete = countVotesFor(planner.id());
            assertThat(votesAfterDelete).isZero();
        }
        @Test
        void foreignKey_WhenCascadeDelete_DeletesChildComments() {
            StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);

            PlannerComment comment = new PlannerComment(
                    planner.id(),
                    testUser.getId(),
                    null,
                    0
            );
            commentRepository.save(comment);

            long commentsBeforeDelete = countCommentsFor(planner.id());
            assertThat(commentsBeforeDelete).isEqualTo(1);

            plannerRepository.deleteById(planner.id());

            long commentsAfterDelete = countCommentsFor(planner.id());
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
            StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner.id(), VoteType.UP);
            voteRepository.save(vote1);

            assertThatThrownBy(() -> inTransaction(() -> {
                PlannerVote vote2 = new PlannerVote(testUser.getId(), planner.id(), VoteType.UP);
                voteRepository.save(vote2);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void uniqueConstraint_WhenDifferentUserSamePlanner_Allowed() {
            StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);
            User otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner.id(), VoteType.UP);
            PlannerVote vote2 = new PlannerVote(otherUser.getId(), planner.id(), VoteType.UP);

            assertThatNoException().isThrownBy(() -> inTransaction(() -> {
                voteRepository.save(vote1);
                voteRepository.save(vote2);
                entityManager.flush();
            }));
        }

        @Test
        void uniqueConstraint_WhenSameUserDifferentPlanner_Allowed() {
            StoredPlanner planner1 = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);
            StoredPlanner planner2 = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner1.id(), VoteType.UP);
            PlannerVote vote2 = new PlannerVote(testUser.getId(), planner2.id(), VoteType.UP);

            assertThatNoException().isThrownBy(() -> inTransaction(() -> {
                voteRepository.save(vote1);
                voteRepository.save(vote2);
                entityManager.flush();
            }));
        }

        @Test
        void uniqueConstraint_WhenDuplicateUsernameSuffix_ThrowsException() {
            String suffix = TestDataFactory.uniqueSuffix("");

            User user1 = TestDataFactory.user()
                    .usernameSuffix(suffix)
                    .build();
            userRepository.save(user1);

            assertThatThrownBy(() -> inTransaction(() -> {
                User user2 = TestDataFactory.user()
                        .usernameSuffix(suffix)
                        .build();
                userRepository.save(user2);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_users_username_suffix");
        }

        @Test
        void uniqueConstraint_WhenDuplicateProviderAndProviderId_ThrowsException() {
            String providerId = "google-" + UUID.randomUUID();
            User user1 = TestDataFactory.user()
                    .providerId(providerId)
                    .build();
            userRepository.save(user1);

            assertThatThrownBy(() -> inTransaction(() -> {
                User user2 = TestDataFactory.user()
                        .providerId(providerId)
                        .build();
                userRepository.save(user2);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_provider_provider_id");
        }

        @Test
        void uniqueConstraint_WhenDifferentProviderSameProviderId_Allowed() {
            String providerId = "shared-" + UUID.randomUUID();
            User user1 = TestDataFactory.user()
                    .provider(AuthProviderType.GOOGLE)
                    .providerId(providerId)
                    .build();
            User user2 = TestDataFactory.user()
                    .provider(AuthProviderType.APPLE)
                    .providerId(providerId)
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
                StoredPlanner planner = TestDataFactory.planner(testUser).title(null).build();
                PlannerRows.insert(plannerSeeds, planner);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void notNullConstraint_WhenMissingOwner_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                StoredPlanner planner = TestDataFactory.planner(testUser).ownerId(null).build();
                PlannerRows.insert(plannerSeeds, planner);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void notNullConstraint_WhenMissingCommentContent_ThrowsException() {
            StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, testUser, true);

            assertThatThrownBy(() -> inTransaction(() -> {
                PlannerComment comment = commentRepository.save(new PlannerComment(
                        planner.id(),
                        testUser.getId(),
                        null,
                        0
                ));
                contentRepository.insertContent(comment.getId(), null);
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'content' cannot be null");
        }

        @Test
        void notNullConstraint_WhenMissingEmail_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                User user = TestDataFactory.user()
                        .email(null)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'email' cannot be null");
        }

        @Test
        void notNullConstraint_WhenMissingProvider_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                User user = TestDataFactory.user()
                        .provider(null)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'provider' cannot be null");
        }

        @Test
        void notNullConstraint_WhenMissingProviderId_ThrowsException() {
            assertThatThrownBy(() -> inTransaction(() -> {
                User user = TestDataFactory.user()
                        .providerId(null)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Column 'provider_id' cannot be null");
        }

        @Test
        void notNullConstraint_WhenEmptyStringAllowed_NullForbidden() {
            StoredPlanner planner1 = TestDataFactory.planner(testUser).title("").build();
            assertThatNoException().isThrownBy(() -> inTransaction(() -> {
                PlannerRows.insert(plannerSeeds, planner1);
                entityManager.flush();
            }));

            assertThatThrownBy(() -> inTransaction(() -> {
                StoredPlanner planner2 = TestDataFactory.planner(testUser).title(null).build();
                PlannerRows.insert(plannerSeeds, planner2);
                entityManager.flush();
            })).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        void notNullConstraint_WhenDefaultValueApplied_PublishedFalse() {
            StoredPlanner planner = TestDataFactory.planner(testUser).build();
            PlannerRows.insert(plannerSeeds, planner);

            StoredPlanner saved = plannerRepository.findAggregate(planner.id()).orElseThrow();
            assertThat(saved.publication().isPublished()).isFalse();
        }

        @Test
        void notNullConstraint_WhenDefaultValueApplied_UpvotesZero() {
            StoredPlanner planner = TestDataFactory.planner(testUser).build();
            PlannerRows.insert(plannerSeeds, planner);
            statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());

            PlannerStats saved = statsRepository.findById(planner.id()).orElseThrow();
            assertThat(saved.getUpvotes()).isZero();
        }
    }
}
