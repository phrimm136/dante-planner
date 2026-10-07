package org.danteplanner.backend.util;
import org.danteplanner.backend.shared.util.ViewerHashUtil;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ViewerHashUtil.
 *
 * <p>Tests hash determinism, uniqueness, and User-Agent sanitization
 * for view deduplication.</p>
 */
class ViewerHashUtilTest {

    private static final UUID PLANNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_PLANNER_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private static final Long USER_ID = 12345L;
    private static final Long OTHER_USER_ID = 67890L;
    private static final String IP_ADDRESS = "192.168.1.100";
    private static final String OTHER_IP_ADDRESS = "192.168.1.200";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final String OTHER_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)";

    @Nested
    class AuthenticatedUserTests {

        @Test
        void hashForAuthenticatedUser_WhenSameInputs_IsDeterministic() {
            // Act
            String hash1 = ViewerHashUtil.hashForAuthenticatedUser(USER_ID, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAuthenticatedUser(USER_ID, PLANNER_ID);

            // Assert
            assertThat(hash2).as("Same inputs should produce same hash").isEqualTo(hash1);
        }

        @Test
        void hashForAuthenticatedUser_WhenCalled_Produces64CharHex() {
            // Act
            String hash = ViewerHashUtil.hashForAuthenticatedUser(USER_ID, PLANNER_ID);

            // Assert
            assertThat(hash.length()).as("SHA-256 hex should be 64 characters").isEqualTo(64);
            assertThat(hash).as("Hash should be lowercase hex").matches("[0-9a-f]+");
        }

        @Test
        void hashForAuthenticatedUser_WhenDifferentUserIds_ProducesDifferentHashes() {
            // Act
            String hash1 = ViewerHashUtil.hashForAuthenticatedUser(USER_ID, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAuthenticatedUser(OTHER_USER_ID, PLANNER_ID);

            // Assert
            assertThat(hash2).as("Different userIds should produce different hashes").isNotEqualTo(hash1);
        }

        @Test
        void hashForAuthenticatedUser_WhenDifferentPlannerIds_ProducesDifferentHashes() {
            // Act
            String hash1 = ViewerHashUtil.hashForAuthenticatedUser(USER_ID, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAuthenticatedUser(USER_ID, OTHER_PLANNER_ID);

            // Assert
            assertThat(hash2).as("Different plannerIds should produce different hashes").isNotEqualTo(hash1);
        }
    }

    @Nested
    class AnonymousUserTests {

        @Test
        void hashForAnonymousUser_WhenSameInputs_IsDeterministic() {
            // Act
            String hash1 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, PLANNER_ID);

            // Assert
            assertThat(hash2).as("Same inputs should produce same hash").isEqualTo(hash1);
        }

        @Test
        void hashForAnonymousUser_WhenCalled_Produces64CharHex() {
            // Act
            String hash = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, PLANNER_ID);

            // Assert
            assertThat(hash.length()).as("SHA-256 hex should be 64 characters").isEqualTo(64);
            assertThat(hash).as("Hash should be lowercase hex").matches("[0-9a-f]+");
        }

        @Test
        void hashForAnonymousUser_WhenDifferentIps_ProducesDifferentHashes() {
            // Act
            String hash1 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAnonymousUser(OTHER_IP_ADDRESS, USER_AGENT, PLANNER_ID);

            // Assert
            assertThat(hash2).as("Different IPs should produce different hashes").isNotEqualTo(hash1);
        }

        @Test
        void hashForAnonymousUser_WhenDifferentUserAgents_ProducesDifferentHashes() {
            // Act
            String hash1 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, OTHER_USER_AGENT, PLANNER_ID);

            // Assert
            assertThat(hash2).as("Different User-Agents should produce different hashes").isNotEqualTo(hash1);
        }

        @Test
        void hashForAnonymousUser_WhenDifferentPlannerIds_ProducesDifferentHashes() {
            // Act
            String hash1 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, OTHER_PLANNER_ID);

            // Assert
            assertThat(hash2).as("Different plannerIds should produce different hashes").isNotEqualTo(hash1);
        }
    }

    @Nested
    class UserAgentSanitizationTests {

        @Test
        void hashForAnonymousUser_WhenNullUserAgent_TreatedAsEmpty() {
            // Act
            String hashNull = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, null, PLANNER_ID);
            String hashEmpty = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, "", PLANNER_ID);

            // Assert
            assertThat(hashEmpty).as("Null User-Agent should be treated as empty string").isEqualTo(hashNull);
        }

        @Test
        void hashForAnonymousUser_WhenUserAgentOver256_Truncates() {
            // Arrange
            String longUserAgent = "A".repeat(300);
            String truncatedUserAgent = "A".repeat(256);

            // Act
            String hashLong = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, longUserAgent, PLANNER_ID);
            String hashTruncated = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, truncatedUserAgent, PLANNER_ID);

            // Assert
            assertThat(hashTruncated).as("User-Agent > 256 chars should be truncated").isEqualTo(hashLong);
        }

        @Test
        void hashForAnonymousUser_WhenUserAgent256_DoesNotTruncate() {
            // Arrange
            String exactUserAgent = "A".repeat(256);
            String shorterUserAgent = "A".repeat(255);

            // Act
            String hashExact = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, exactUserAgent, PLANNER_ID);
            String hashShorter = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, shorterUserAgent, PLANNER_ID);

            // Assert
            assertThat(hashShorter).as("256 chars should not equal 255 chars").isNotEqualTo(hashExact);
        }

        @Test
        void hashForAnonymousUser_WhenShortUserAgent_DoesNotTruncate() {
            // Arrange - 100 chars, well under limit
            String shortUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

            // Act - both should produce same hash
            String hash1 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, shortUserAgent, PLANNER_ID);
            String hash2 = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, shortUserAgent, PLANNER_ID);

            // Assert
            assertThat(hash2).as("Short User-Agent should not be modified").isEqualTo(hash1);
        }
    }

    @Nested
    class CrossMethodTests {

        @Test
        void hash_WhenAuthenticatedVsAnonymous_ProducesDifferentHashes() {
            // Act
            String authHash = ViewerHashUtil.hashForAuthenticatedUser(USER_ID, PLANNER_ID);
            String anonHash = ViewerHashUtil.hashForAnonymousUser(IP_ADDRESS, USER_AGENT, PLANNER_ID);

            // Assert
            assertThat(anonHash).as("Auth and anon hashes should differ").isNotEqualTo(authHash);
        }
    }
}
