package org.danteplanner.backend.repository;

import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.config.TestDataInitializer;
import org.danteplanner.backend.entity.*;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
@ActiveProfiles("test")
@Transactional
@Import(TestConfig.class)
class PlannerRepositoryConstraintTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        voteRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .toList());
        entityManager.flush();
        entityManager.clear();

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
    }

    @Nested
    @DisplayName("Foreign Key Constraint Tests")
    class ForeignKeyTests {

        @org.junit.jupiter.api.Disabled("H2 database does not enforce FK constraints in test mode. Enable for MySQL/PostgreSQL.")
        @Test
        @DisplayName("Invalid planner ID in vote throws FK constraint exception")
        void foreignKey_InvalidPlannerId_ThrowsException() {
            assertThatThrownBy(() -> {
                PlannerVote vote = new PlannerVote(testUser.getId(), UUID.randomUUID(), VoteType.UP);
                voteRepository.save(vote);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @org.junit.jupiter.api.Disabled("H2 database does not enforce FK constraints in test mode. Enable for MySQL/PostgreSQL.")
        @Test
        @DisplayName("Invalid user ID in vote throws FK constraint exception")
        void foreignKey_InvalidUserId_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            assertThatThrownBy(() -> {
                PlannerVote vote = new PlannerVote(99999L, planner.getId(), VoteType.UP);
                voteRepository.save(vote);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @org.junit.jupiter.api.Disabled("H2 database does not enforce FK constraints in test mode. Enable for MySQL/PostgreSQL.")
        @Test
        @DisplayName("Invalid planner ID in comment throws FK constraint exception")
        void foreignKey_InvalidPlannerIdInComment_ThrowsException() {
            assertThatThrownBy(() -> {
                PlannerComment comment = new PlannerComment(
                        UUID.randomUUID(),
                        testUser.getId(),
                        "Test comment",
                        null,
                        0
                );
                commentRepository.save(comment);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @org.junit.jupiter.api.Disabled("H2 database does not enforce FK constraints in test mode. Enable for MySQL/PostgreSQL.")
        @Test
        @DisplayName("Invalid user ID in comment throws FK constraint exception")
        void foreignKey_InvalidUserIdInComment_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            assertThatThrownBy(() -> {
                PlannerComment comment = new PlannerComment(
                        planner.getId(),
                        99999L,
                        "Test comment",
                        null,
                        0
                );
                commentRepository.save(comment);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @org.junit.jupiter.api.Disabled("H2 database does not enforce FK constraints in test mode. Enable for MySQL/PostgreSQL.")
        @Test
        @DisplayName("Cascade delete on planner removes associated votes")
        void foreignKey_CascadeDelete_DeletesChildVotes() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerVote vote = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
            voteRepository.save(vote);
            entityManager.flush();

            long votesBeforeDelete = voteRepository.count();
            assertThat(votesBeforeDelete).isEqualTo(1);

            plannerRepository.delete(planner);
            entityManager.flush();

            long votesAfterDelete = voteRepository.count();
            assertThat(votesAfterDelete).isZero();
        }

        @org.junit.jupiter.api.Disabled("H2 database does not enforce FK constraints in test mode. Enable for MySQL/PostgreSQL.")
        @Test
        @DisplayName("Cascade delete on planner removes associated comments")
        void foreignKey_CascadeDelete_DeletesChildComments() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerComment comment = new PlannerComment(
                    planner.getId(),
                    testUser.getId(),
                    "Test comment",
                    null,
                    0
            );
            commentRepository.save(comment);
            entityManager.flush();

            long commentsBeforeDelete = commentRepository.count();
            assertThat(commentsBeforeDelete).isEqualTo(1);

            plannerRepository.delete(planner);
            entityManager.flush();

            long commentsAfterDelete = commentRepository.count();
            assertThat(commentsAfterDelete).isZero();
        }
    }

    @Nested
    @DisplayName("UNIQUE Constraint Tests")
    class UniqueConstraintTests {

        @Test
        @DisplayName("Duplicate vote (same user + planner) throws UNIQUE constraint exception")
        void uniqueConstraint_DuplicateVote_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
            voteRepository.save(vote1);
            entityManager.flush();
            entityManager.clear();

            assertThatThrownBy(() -> {
                PlannerVote vote2 = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
                voteRepository.save(vote2);
                entityManager.flush();
            }).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        @DisplayName("Different user same planner allows separate votes")
        void uniqueConstraint_DifferentUserSamePlanner_Allowed() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
            User otherUser = TestDataFactory.createTestUser(userRepository, "other@example.com");

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner.getId(), VoteType.UP);
            voteRepository.save(vote1);

            PlannerVote vote2 = new PlannerVote(otherUser.getId(), planner.getId(), VoteType.UP);
            voteRepository.save(vote2);

            assertThatNoException().isThrownBy(() -> entityManager.flush());
        }

        @Test
        @DisplayName("Same user different planner allows separate votes")
        void uniqueConstraint_SameUserDifferentPlanner_Allowed() {
            Planner planner1 = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
            Planner planner2 = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerVote vote1 = new PlannerVote(testUser.getId(), planner1.getId(), VoteType.UP);
            voteRepository.save(vote1);

            PlannerVote vote2 = new PlannerVote(testUser.getId(), planner2.getId(), VoteType.UP);
            voteRepository.save(vote2);

            assertThatNoException().isThrownBy(() -> entityManager.flush());
        }

        @Test
        @DisplayName("Duplicate username_suffix throws UNIQUE constraint exception")
        void uniqueConstraint_DuplicateUsernameSuffix_ThrowsException() {
            String suffix = "test1";

            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider("google")
                    .providerId("google-1")
                    .usernameKeyword("TEST")
                    .usernameSuffix(suffix)
                    .role(UserRole.NORMAL)
                    .build();
            userRepository.save(user1);
            entityManager.flush();

            assertThatThrownBy(() -> {
                User user2 = User.builder()
                        .email("user2@example.com")
                        .provider("google")
                        .providerId("google-2")
                        .usernameKeyword("TEST")
                        .usernameSuffix(suffix)
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user2);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Duplicate provider + providerId throws UNIQUE constraint exception")
        void uniqueConstraint_DuplicateProviderAndProviderId_ThrowsException() {
            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider("google")
                    .providerId("google-123")
                    .usernameKeyword("TEST")
                    .usernameSuffix("00001")
                    .role(UserRole.NORMAL)
                    .build();
            userRepository.save(user1);
            entityManager.flush();

            assertThatThrownBy(() -> {
                User user2 = User.builder()
                        .email("user2@example.com")
                        .provider("google")
                        .providerId("google-123")
                        .usernameKeyword("TEST")
                        .usernameSuffix("00002")
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user2);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Different provider same providerId allows separate users")
        void uniqueConstraint_DifferentProviderSameProviderId_Allowed() {
            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider("google")
                    .providerId("123")
                    .usernameKeyword("TEST")
                    .usernameSuffix("00001")
                    .role(UserRole.NORMAL)
                    .build();
            userRepository.save(user1);

            User user2 = User.builder()
                    .email("user2@example.com")
                    .provider("apple")
                    .providerId("123")
                    .usernameKeyword("TEST")
                    .usernameSuffix("00002")
                    .role(UserRole.NORMAL)
                    .build();
            userRepository.save(user2);

            assertThatNoException().isThrownBy(() -> entityManager.flush());
        }
    }

    @Nested
    @DisplayName("NOT NULL Constraint Tests")
    class NotNullTests {

        @Test
        @DisplayName("Missing planner title throws NOT NULL constraint exception")
        void notNullConstraint_MissingTitle_ThrowsException() {
            assertThatThrownBy(() -> {
                Planner planner = Planner.builder()
                        .id(UUID.randomUUID())
                        .user(testUser)
                        .plannerType(PlannerType.MIRROR_DUNGEON)
                        .title(null)
                        .category("5F")
                        .content("{}")
                        .contentVersion(1)
                        .savedAt(Instant.now())
                        .build();
                plannerRepository.save(planner);
                entityManager.flush();
            }).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        @DisplayName("Missing planner owner throws NOT NULL constraint exception")
        void notNullConstraint_MissingOwner_ThrowsException() {
            assertThatThrownBy(() -> {
                Planner planner = Planner.builder()
                        .id(UUID.randomUUID())
                        .plannerType(PlannerType.MIRROR_DUNGEON)
                        .title("Test Title")
                        .category("5F")
                        .content("{}")
                        .contentVersion(1)
                        .savedAt(Instant.now())
                        .build();
                plannerRepository.save(planner);
                entityManager.flush();
            }).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        @DisplayName("Missing comment content throws NOT NULL constraint exception")
        void notNullConstraint_MissingCommentContent_ThrowsException() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            assertThatThrownBy(() -> {
                PlannerComment comment = new PlannerComment(
                        planner.getId(),
                        testUser.getId(),
                        null,
                        null,
                        0
                );
                commentRepository.save(comment);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Missing user email throws NOT NULL constraint exception")
        void notNullConstraint_MissingEmail_ThrowsException() {
            assertThatThrownBy(() -> {
                User user = User.builder()
                        .email(null)
                        .provider("google")
                        .providerId("google-123")
                        .usernameKeyword("TEST")
                        .usernameSuffix("test1")
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Missing user provider throws NOT NULL constraint exception")
        void notNullConstraint_MissingProvider_ThrowsException() {
            assertThatThrownBy(() -> {
                User user = User.builder()
                        .email("test@example.com")
                        .provider(null)
                        .providerId("google-123")
                        .usernameKeyword("TEST")
                        .usernameSuffix("test1")
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Missing user providerId throws NOT NULL constraint exception")
        void notNullConstraint_MissingProviderId_ThrowsException() {
            assertThatThrownBy(() -> {
                User user = User.builder()
                        .email("test@example.com")
                        .provider("google")
                        .providerId(null)
                        .usernameKeyword("TEST")
                        .usernameSuffix("test1")
                        .role(UserRole.NORMAL)
                        .build();
                userRepository.save(user);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Empty string title allowed, null forbidden")
        void notNullConstraint_EmptyStringAllowed_NullForbidden() {
            Planner planner1 = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .title("")
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .savedAt(Instant.now())
                    .build();
            plannerRepository.save(planner1);
            assertThatNoException().isThrownBy(() -> entityManager.flush());

            entityManager.clear();

            assertThatThrownBy(() -> {
                Planner planner2 = Planner.builder()
                        .id(UUID.randomUUID())
                        .user(testUser)
                        .plannerType(PlannerType.MIRROR_DUNGEON)
                        .title(null)
                        .category("5F")
                        .content("{}")
                        .contentVersion(1)
                        .savedAt(Instant.now())
                        .build();
                plannerRepository.save(planner2);
                entityManager.flush();
            }).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(DataIntegrityViolationException.class),
                    e -> assertThat(e).isInstanceOf(ConstraintViolationException.class)
            );
        }

        @Test
        @DisplayName("Default value applied for published field (false)")
        void notNullConstraint_DefaultValueApplied_PublishedFalse() {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .title("Test")
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .savedAt(Instant.now())
                    .build();
            plannerRepository.save(planner);
            entityManager.flush();

            Planner saved = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(saved.getPublished()).isFalse();
        }

        @Test
        @DisplayName("Default value applied for upvotes field (0)")
        void notNullConstraint_DefaultValueApplied_UpvotesZero() {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .title("Test")
                    .category("5F")
                    .content("{}")
                    .contentVersion(1)
                    .savedAt(Instant.now())
                    .build();
            plannerRepository.save(planner);
            entityManager.flush();

            Planner saved = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(saved.getUpvotes()).isZero();
        }

        @Test
        @DisplayName("Default value applied for comment upvoteCount (0)")
        void notNullConstraint_DefaultValueApplied_CommentUpvoteCountZero() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);

            PlannerComment comment = new PlannerComment(
                    planner.getId(),
                    testUser.getId(),
                    "Test content",
                    null,
                    0
            );
            commentRepository.save(comment);
            entityManager.flush();

            PlannerComment saved = commentRepository.findById(comment.getId()).orElseThrow();
            assertThat(saved.getUpvoteCount()).isZero();
        }
    }
}
