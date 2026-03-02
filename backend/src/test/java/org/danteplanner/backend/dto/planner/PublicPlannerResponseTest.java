package org.danteplanner.backend.dto.planner;

import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
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
        void fromEntity_ReturnsUsernameEpithet() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(TEST_KEYWORD, response.getAuthorUsernameEpithet());
        }

        @Test
        @DisplayName("Should return username suffix from user entity")
        void fromEntity_ReturnsUsernameSuffix() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(TEST_SUFFIX, response.getAuthorUsernameSuffix());
        }

        @Test
        @DisplayName("Should handle null email while returning username components")
        void fromEntity_NullEmail_ReturnsUsernameComponents() {
            User user = createTestUser(null);
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(TEST_KEYWORD, response.getAuthorUsernameEpithet());
            assertEquals(TEST_SUFFIX, response.getAuthorUsernameSuffix());
        }
    }

    @Nested
    @DisplayName("commentCount Tests")
    class CommentCountTests {

        @Test
        @DisplayName("commentCount is null when fromEntity called without setter")
        void fromEntity_CommentCountIsNullByDefault() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertNull(response.getCommentCount());
        }

        @Test
        @DisplayName("setCommentCount stores the value correctly")
        void setCommentCount_StoresValue() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            response.setCommentCount(7L);
            assertEquals(7L, response.getCommentCount());
        }

        @Test
        @DisplayName("setCommentCount with zero is valid")
        void setCommentCount_Zero_IsValid() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            response.setCommentCount(0L);
            assertEquals(0L, response.getCommentCount());
        }
    }

    @Nested
    @DisplayName("fromEntity Field Mapping Tests")
    class FieldMappingTests {

        @Test
        @DisplayName("Should map all fields correctly from Planner entity")
        void fromEntity_MapsAllFieldsCorrectly() {
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
                    () -> assertEquals(plannerId, response.getId()),
                    () -> assertEquals("Test Planner Title", response.getTitle()),
                    () -> assertEquals("5F", response.getCategory()),
                    () -> assertEquals(keywords, response.getSelectedKeywords()),
                    () -> assertEquals(TEST_KEYWORD, response.getAuthorUsernameEpithet()),
                    () -> assertEquals(TEST_SUFFIX, response.getAuthorUsernameSuffix()),
                    () -> assertEquals(10, response.getUpvotes()),
                    () -> assertEquals(createdAt, response.getCreatedAt())
            );
        }
    }

    private static User createTestUser(String email) {
        return User.builder()
                .id(1L)
                .email(email)
                .provider("google")
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
