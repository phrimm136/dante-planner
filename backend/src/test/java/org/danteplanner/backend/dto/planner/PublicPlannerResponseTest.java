package org.danteplanner.backend.dto.planner;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerType;
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
    @DisplayName("fromCatalog Author Username Tests")
    class AuthorUsernameTests {

        @Test
        @DisplayName("Should return username keyword from user entity")
        void fromCatalog_WhenMapped_ReturnsUsernameEpithet() {
            User user = createTestUser("test@example.com");
            PlannerCatalog row = createCatalogRow();
            PublicPlannerResponse response = fromCatalog(row, user);
            assertEquals(TEST_KEYWORD, response.authorUsernameEpithet());
        }

        @Test
        @DisplayName("Should return username suffix from user entity")
        void fromCatalog_WhenMapped_ReturnsUsernameSuffix() {
            User user = createTestUser("test@example.com");
            PlannerCatalog row = createCatalogRow();
            PublicPlannerResponse response = fromCatalog(row, user);
            assertEquals(TEST_SUFFIX, response.authorUsernameSuffix());
        }

        @Test
        @DisplayName("Should handle null email while returning username components")
        void fromCatalog_NullEmail_ReturnsUsernameComponents() {
            User user = createTestUser(null);
            PlannerCatalog row = createCatalogRow();
            PublicPlannerResponse response = fromCatalog(row, user);
            assertEquals(TEST_KEYWORD, response.authorUsernameEpithet());
            assertEquals(TEST_SUFFIX, response.authorUsernameSuffix());
        }
    }

    @Nested
    @DisplayName("commentCount Tests")
    class CommentCountTests {

        @Test
        @DisplayName("commentCount is null when fromCatalog called without setter")
        void fromCatalog_WhenNoSetter_CommentCountIsNull() {
            User user = createTestUser("test@example.com");
            PlannerCatalog row = createCatalogRow();
            PublicPlannerResponse response = fromCatalog(row, user);
            assertNull(response.commentCount());
        }

        @Test
        @DisplayName("setCommentCount stores the value correctly")
        void commentCount_WhenSetViaBuilder_StoresValue() {
            User user = createTestUser("test@example.com");
            PlannerCatalog row = createCatalogRow();
            PublicPlannerResponse response = fromCatalog(row, user)
                    .toBuilder().commentCount(7L).build();
            assertEquals(7L, response.commentCount());
        }

        @Test
        @DisplayName("setCommentCount with zero is valid")
        void setCommentCount_Zero_IsValid() {
            User user = createTestUser("test@example.com");
            PlannerCatalog row = createCatalogRow();
            PublicPlannerResponse response = fromCatalog(row, user)
                    .toBuilder().commentCount(0L).build();
            assertEquals(0L, response.commentCount());
        }
    }

    @Nested
    @DisplayName("fromCatalog Field Mapping Tests")
    class FieldMappingTests {

        @Test
        @DisplayName("Should map all fields correctly from catalog row")
        void fromCatalog_WhenMapped_MapsAllFields() {
            User user = createTestUser("test@example.com");
            UUID plannerId = UUID.randomUUID();
            Instant createdAt = Instant.now();
            Instant firstPublishedAt = Instant.now();
            Set<String> keywords = Set.of("keyword1", "keyword2");

            PlannerCatalog row = PlannerCatalog.builder()
                    .plannerId(plannerId)
                    .plannerType(PlannerType.MIRROR_DUNGEON)
                    .title("Test Planner Title")
                    .category("5F")
                    .selectedKeywords(keywords)
                    .firstPublishedAt(firstPublishedAt)
                    .build();

            PublicPlannerResponse response = PublicPlannerResponse.fromCatalog(
                    row, user, createdAt, 10, 3, null, null);

            assertAll("All fields should be mapped correctly",
                    () -> assertEquals(plannerId, response.id()),
                    () -> assertEquals("Test Planner Title", response.title()),
                    () -> assertEquals("5F", response.category()),
                    () -> assertEquals(PlannerType.MIRROR_DUNGEON, response.plannerType()),
                    () -> assertEquals(keywords, response.selectedKeywords()),
                    () -> assertEquals(TEST_KEYWORD, response.authorUsernameEpithet()),
                    () -> assertEquals(TEST_SUFFIX, response.authorUsernameSuffix()),
                    () -> assertEquals(10, response.upvotes()),
                    () -> assertEquals(3, response.viewCount()),
                    () -> assertEquals(createdAt, response.createdAt()),
                    () -> assertEquals(firstPublishedAt, response.firstPublishedAt())
            );
        }
    }

    private static PublicPlannerResponse fromCatalog(PlannerCatalog row, User author) {
        return PublicPlannerResponse.fromCatalog(row, author, Instant.now(), 5, 0, null, null);
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

    private static PlannerCatalog createCatalogRow() {
        return PlannerCatalog.builder()
                .plannerId(UUID.randomUUID())
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .title("Test Planner")
                .category("5F")
                .firstPublishedAt(Instant.now())
                .build();
    }
}
