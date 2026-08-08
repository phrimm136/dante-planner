package org.danteplanner.backend.moderation;

import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.moderation.dto.BanStatusResponse;
import org.danteplanner.backend.moderation.dto.ModeratedUserResponse;
import org.danteplanner.backend.moderation.dto.PlannerActionResponse;
import org.danteplanner.backend.moderation.dto.UnpublishPlannerResponse;
import org.danteplanner.backend.shared.config.JacksonConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the JSON the moderation endpoints put on the wire, field name by field name, so a
 * change of the Java type carrying a response cannot change what a client receives.
 */
class ModerationResponseShapeTest {

    private static final UUID PLANNER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant BANNED_AT = Instant.parse("2026-03-04T05:06:07Z");
    private static final Instant TIMEOUT_UNTIL = Instant.parse("2026-03-05T06:07:08Z");

    /** The application's own mapper: the shape asserted here is the one clients receive. */
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper(List.of());

    @Test
    void unpublishPlanner_WhenModeratorUnpublishes_CarriesPlannerIdPublishedAndMessage() {
        Map<String, Object> json = serialize(new UnpublishPlannerResponse(
                PLANNER_ID, false, "Planner unpublished successfully"));

        assertThat(json).containsOnlyKeys("plannerId", "published", "message");
        assertThat(json).containsEntry("plannerId", PLANNER_ID.toString());
        assertThat(json).containsEntry("published", false);
        assertThat(json).containsEntry("message", "Planner unpublished successfully");
    }

    @Test
    void banUser_WhenAdminBans_CarriesBannedAndMessage() {
        Map<String, Object> json = serialize(new BanStatusResponse(true, "User banned successfully"));

        assertThat(json).containsOnlyKeys("banned", "message");
        assertThat(json).containsEntry("banned", true);
        assertThat(json).containsEntry("message", "User banned successfully");
    }

    @Test
    void unbanUser_WhenAdminUnbans_CarriesBannedAndMessage() {
        Map<String, Object> json = serialize(new BanStatusResponse(false, "User unbanned successfully"));

        assertThat(json).containsOnlyKeys("banned", "message");
        assertThat(json).containsEntry("banned", false);
        assertThat(json).containsEntry("message", "User unbanned successfully");
    }

    @Test
    void takedownPlanner_WhenModeratorTakesDown_CarriesPlannerIdAndMessage() {
        Map<String, Object> json = serialize(
                new PlannerActionResponse(PLANNER_ID, "Planner taken down successfully"));

        assertThat(json).containsOnlyKeys("plannerId", "message");
        assertThat(json).containsEntry("plannerId", PLANNER_ID.toString());
        assertThat(json).containsEntry("message", "Planner taken down successfully");
    }

    @Test
    void getAllUsers_WhenUserIsRestricted_CarriesRosterFieldsWithIsoTimestamps() {
        Map<String, Object> json = serialize(roster("Naive", "ab12c", "NORMAL",
                true, BANNED_AT, true, TIMEOUT_UNTIL));

        assertThat(json).containsOnlyKeys("usernameEpithet", "usernameSuffix", "role",
                "isBanned", "bannedAt", "isTimedOut", "timeoutUntil");
        assertThat(json).containsEntry("usernameEpithet", "Naive");
        assertThat(json).containsEntry("usernameSuffix", "ab12c");
        assertThat(json).containsEntry("role", "NORMAL");
        assertThat(json).containsEntry("isBanned", true);
        assertThat(json).containsEntry("bannedAt", "2026-03-04T05:06:07Z");
        assertThat(json).containsEntry("isTimedOut", true);
        assertThat(json).containsEntry("timeoutUntil", "2026-03-05T06:07:08Z");
    }

    @Test
    void getAllUsers_WhenUserIsUnrestricted_OmitsBothRestrictionTimestamps() {
        Map<String, Object> json = serialize(roster("Naive", "ab12c", "NORMAL",
                false, null, false, null));

        assertThat(json).containsOnlyKeys("usernameEpithet", "usernameSuffix", "role",
                "isBanned", "isTimedOut");
        assertThat(json).containsEntry("isBanned", false);
        assertThat(json).containsEntry("isTimedOut", false);
    }

    private static ModeratedUserResponse roster(String epithet, String suffix, String role,
            boolean banned, Instant bannedAt, boolean timedOut, Instant timeoutUntil) {
        return new ModeratedUserResponse(epithet, suffix, role, banned, bannedAt, timedOut, timeoutUntil);
    }

    private Map<String, Object> serialize(Object response) {
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(response), new TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("response is not serializable", e);
        }
    }
}
