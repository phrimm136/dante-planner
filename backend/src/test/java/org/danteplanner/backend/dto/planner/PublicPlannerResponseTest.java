package org.danteplanner.backend.dto.planner;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicPlannerResponseTest {

    private static final String TEST_KEYWORD = "W_CORP";
    private static final String TEST_SUFFIX = "7k3mx";

    @Nested
    @DisplayName("fromEntity Author Username Tests")
    class AuthorUsernameTests {

        @Test
        @DisplayName("Should return username keyword from user entity")
        void fromEntity_WhenMapped_ReturnsUsernameEpithet() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(TEST_KEYWORD, response.authorUsernameEpithet());
        }

        @Test
        @DisplayName("Should return username suffix from user entity")
        void fromEntity_WhenMapped_ReturnsUsernameSuffix() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(TEST_SUFFIX, response.authorUsernameSuffix());
        }

        @Test
        @DisplayName("Should handle null email while returning username components")
        void fromEntity_NullEmail_ReturnsUsernameComponents() {
            User user = createTestUser(null);
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(TEST_KEYWORD, response.authorUsernameEpithet());
            assertEquals(TEST_SUFFIX, response.authorUsernameSuffix());
        }
    }

    @Nested
    @DisplayName("commentCount Tests")
    class CommentCountTests {

        @Test
        @DisplayName("commentCount is null when fromEntity called without setter")
        void fromEntity_WhenNoSetter_CommentCountIsNull() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertNull(response.commentCount());
        }

        @Test
        @DisplayName("setCommentCount stores the value correctly")
        void commentCount_WhenSetViaBuilder_StoresValue() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner)
                    .toBuilder().commentCount(7L).build();
            assertEquals(7L, response.commentCount());
        }

        @Test
        @DisplayName("setCommentCount with zero is valid")
        void setCommentCount_Zero_IsValid() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner)
                    .toBuilder().commentCount(0L).build();
            assertEquals(0L, response.commentCount());
        }
    }

    @Nested
    @DisplayName("fromEntity Field Mapping Tests")
    class FieldMappingTests {

        @Test
        @DisplayName("Should map all fields correctly from Planner entity")
        void fromEntity_WhenMapped_MapsAllFields() {
            User user = createTestUser("test@example.com");
            UUID plannerId = UUID.randomUUID();
            Instant createdAt = Instant.now();
            Set<String> keywords = Set.of("keyword1", "keyword2");

            Planner planner = Planner.builder()
                    .id(plannerId)
                    .user(user)
                    .title("Test Planner Title")
                    .category("5F")
                    .selectedKeywords(keywords)
                    .upvotes(10)
                    .createdAt(createdAt)
                    .build();

            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);

            assertAll("All fields should be mapped correctly",
                    () -> assertEquals(plannerId, response.id()),
                    () -> assertEquals("Test Planner Title", response.title()),
                    () -> assertEquals("5F", response.category()),
                    () -> assertEquals(keywords, response.selectedKeywords()),
                    () -> assertEquals(TEST_KEYWORD, response.authorUsernameEpithet()),
                    () -> assertEquals(TEST_SUFFIX, response.authorUsernameSuffix()),
                    () -> assertEquals(10, response.upvotes()),
                    () -> assertEquals(createdAt, response.createdAt())
            );
        }
    }

    private static User createTestUser(String email) {
        return User.builder()
                .id(1L)
                .email(email)
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet(TEST_KEYWORD)
                .usernameSuffix(TEST_SUFFIX)
                .build();
    }

    private static Planner createTestPlanner(User user) {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Test Planner")
                .category("5F")
                .upvotes(5)
                .createdAt(Instant.now())
                .build();
    }
}
