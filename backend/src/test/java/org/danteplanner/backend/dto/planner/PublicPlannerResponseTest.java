package org.danteplanner.backend.dto.planner;

import org.danteplanner.backend.entity.MDCategory;
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

    private static final String ANONYMOUS = "Anonymous";

    @Nested
    @DisplayName("fromEntity Author Anonymization Tests")
    class AuthorAnonymizationTests {

        @Test
        @DisplayName("Should return Anonymous when user has null email")
        void fromEntity_NullEmail_ReturnsAnonymous() {
            User user = createTestUser(null);
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(ANONYMOUS, response.getAuthorName());
        }

        @Test
        @DisplayName("Should return Anonymous when user has empty email")
        void fromEntity_EmptyEmail_ReturnsAnonymous() {
            User user = createTestUser("");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(ANONYMOUS, response.getAuthorName());
        }

        @Test
        @DisplayName("Should return Anonymous when user has valid email")
        void fromEntity_ValidEmail_ReturnsAnonymous() {
            User user = createTestUser("test@example.com");
            Planner planner = createTestPlanner(user);
            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);
            assertEquals(ANONYMOUS, response.getAuthorName());
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
                    .category(MDCategory.F5)
                    .selectedKeywords(keywords)
                    .upvotes(10)
                    .downvotes(3)
                    .createdAt(createdAt)
                    .build();

            PublicPlannerResponse response = PublicPlannerResponse.fromEntity(planner);

            assertAll("All fields should be mapped correctly",
                    () -> assertEquals(plannerId, response.getId()),
                    () -> assertEquals("Test Planner Title", response.getTitle()),
                    () -> assertEquals(MDCategory.F5, response.getCategory()),
                    () -> assertEquals(keywords, response.getSelectedKeywords()),
                    () -> assertEquals(ANONYMOUS, response.getAuthorName()),
                    () -> assertEquals(10, response.getUpvotes()),
                    () -> assertEquals(3, response.getDownvotes()),
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
                .build();
    }

    private static Planner createTestPlanner(User user) {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Test Planner")
                .category(MDCategory.F5)
                .upvotes(5)
                .downvotes(2)
                .createdAt(Instant.now())
                .build();
    }
}
