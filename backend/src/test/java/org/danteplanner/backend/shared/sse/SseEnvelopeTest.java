package org.danteplanner.backend.shared.sse;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.support.TestJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseEnvelopeTest {

    private static final UUID PLANNER = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID DEVICE = UUID.fromString("88888888-9999-4aaa-8bbb-cccccccccccc");

    private static final String USER_EVENT_EXCLUDING_A_DEVICE = "{\"type\":\"notify:comment\",\"userId\":7,"
            + "\"entityId\":\"n1\",\"excludeDeviceId\":\"88888888-9999-4aaa-8bbb-cccccccccccc\","
            + "\"payload\":{\"id\":\"n1\"}}";
    private static final String USER_EVENT = "{\"type\":\"notify:comment\",\"userId\":7,\"entityId\":\"n1\","
            + "\"payload\":{\"id\":\"n1\"}}";
    private static final String SETTINGS_INVALIDATION = "{\"type\":\"settings:invalidated\",\"userId\":7}";
    private static final String COMMENT_EVENT = "{\"type\":\"comment:added\","
            + "\"plannerId\":\"11111111-2222-4333-8444-555555555555\",\"entityId\":\"c1\",\"excludeUserId\":42,"
            + "\"payload\":{\"id\":\"c1\"}}";
    private static final String BROADCAST_EXCLUDING_A_USER = "{\"type\":\"notify:published\",\"excludeUserId\":42,"
            + "\"payload\":{\"plannerId\":\"p1\"}}";
    private static final String BROADCAST = "{\"type\":\"notify:published\",\"payload\":{\"plannerId\":\"p1\"}}";
    private static final String ACCOUNT_SUSPENDED = "{\"type\":\"account_suspended\",\"userId\":7,"
            + "\"payload\":{\"reason\":\"spam\"}}";

    private final ObjectMapper objectMapper = TestJsonMappers.springDefaults().rebuild()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    @Test
    void userEventExcludingADevice_WhenRoundTripped_MatchesThePreviousReleasesJson() {
        assertRoundTrip(new SseEnvelope.UserEvent(7L, SseEventType.NOTIFY_COMMENT, "n1",
                new SseEnvelope.DeviceExclusion.Device(DEVICE), Map.of("id", "n1")),
                SseChannel.USER, USER_EVENT_EXCLUDING_A_DEVICE);
    }

    @Test
    void userEvent_WhenRoundTripped_MatchesThePreviousReleasesJson() {
        assertRoundTrip(new SseEnvelope.UserEvent(7L, SseEventType.NOTIFY_COMMENT, "n1",
                new SseEnvelope.DeviceExclusion.None(), Map.of("id", "n1")), SseChannel.USER, USER_EVENT);
    }

    @Test
    void settingsInvalidation_WhenRoundTripped_MatchesThePreviousReleasesJson() {
        assertRoundTrip(new SseEnvelope.SettingsInvalidation(7L), SseChannel.USER, SETTINGS_INVALIDATION);
    }

    @Test
    void commentEvent_WhenRoundTripped_MatchesThePreviousReleasesJson() {
        assertRoundTrip(new SseEnvelope.CommentEvent(PLANNER, SseEventType.COMMENT_ADDED, "c1", new SseEnvelope.UserExclusion.User(42L),
                Map.of("id", "c1")), SseChannel.COMMENT, COMMENT_EVENT);
    }

    @Test
    void broadcastExcludingAUser_WhenRoundTripped_MatchesThePreviousReleasesJson() {
        assertRoundTrip(new SseEnvelope.Broadcast(new SseEnvelope.UserExclusion.User(42L),
                SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p1")), SseChannel.BROADCAST,
                BROADCAST_EXCLUDING_A_USER);
    }

    @Test
    void broadcast_WhenRoundTripped_MatchesThePreviousReleasesJson() {
        assertRoundTrip(new SseEnvelope.Broadcast(new SseEnvelope.UserExclusion.None(),
                SseEventType.NOTIFY_PUBLISHED, Map.of("plannerId", "p1")), SseChannel.BROADCAST, BROADCAST);
    }

    @Test
    void accountSuspended_WhenRoundTripped_MatchesThePreviousReleasesJson() {
        assertRoundTrip(new SseEnvelope.AccountSuspended(7L, Map.of("reason", "spam")), SseChannel.USER,
                ACCOUNT_SUSPENDED);
    }

    @Test
    void commentEnvelope_WhenItExcludesNobody_IsReadForEverySubscriber() {
        String json = COMMENT_EVENT.replace("\"excludeUserId\":42,", "");

        assertRoundTrip(new SseEnvelope.CommentEvent(PLANNER, SseEventType.COMMENT_ADDED, "c1",
                new SseEnvelope.UserExclusion.None(), Map.of("id", "c1")), SseChannel.COMMENT, json);
    }

    @Test
    void commentEnvelope_WhenItCarriesNoPlanner_IsRefused() {
        SseEnvelope.Wire wire = objectMapper.readValue(USER_EVENT, SseEnvelope.Wire.class);

        assertThatThrownBy(() -> wire.on(SseChannel.COMMENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plannerId");
    }

    @Test
    void deviceExclusion_WhenADeviceIsExcluded_ExcludesOnlyThatDevice() {
        SseEnvelope.DeviceExclusion exclusion = new SseEnvelope.DeviceExclusion.Device(DEVICE);

        assertThat(exclusion.excludes(DEVICE)).isTrue();
        assertThat(exclusion.excludes(UUID.randomUUID())).isFalse();
        assertThat(new SseEnvelope.DeviceExclusion.None().excludes(DEVICE)).isFalse();
    }

    @Test
    void userExclusion_WhenNoUserIsExcluded_ExcludesNobody() {
        assertThat(new SseEnvelope.UserExclusion.None().excludes(42L)).isFalse();
        assertThat(new SseEnvelope.UserExclusion.User(42L).excludes(42L)).isTrue();
        assertThat(new SseEnvelope.UserExclusion.User(42L).excludes(7L)).isFalse();
    }

    private void assertRoundTrip(SseEnvelope envelope, SseChannel channel, String previousReleaseJson) {
        assertThat(objectMapper.writeValueAsString(envelope)).isEqualTo(previousReleaseJson);
        assertThat(objectMapper.readValue(previousReleaseJson, SseEnvelope.Wire.class).on(channel))
                .isEqualTo(envelope);
    }
}
