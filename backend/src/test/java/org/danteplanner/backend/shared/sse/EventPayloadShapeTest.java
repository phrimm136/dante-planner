package org.danteplanner.backend.shared.sse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.planner.dto.PlannerPublishedPayload;
import org.danteplanner.backend.shared.config.JacksonConfig;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the JSON each SSE payload puts on the wire. Payload field names are a client contract
 * the browser reads by name, so the Java type carrying a payload may change while the emitted
 * keys may not.
 */
class EventPayloadShapeTest {

    private static final UUID PLANNER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID COMMENT_PUBLIC_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final UUID NOTIFICATION_PUBLIC_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    /** The application's own mapper: the shape asserted here is the one clients receive. */
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper(List.of());

    @Test
    void accountSuspended_WhenTimeout_CarriesSuspensionTypeReasonAndDuration() {
        Map<String, Object> json = serialize(suspension(SuspensionType.TIMED_OUT, "spam", 60));

        assertThat(json).containsOnlyKeys("suspensionType", "reason", "durationMinutes");
        assertThat(json).containsEntry("suspensionType", "TIMEOUT");
        assertThat(json).containsEntry("reason", "spam");
        assertThat(json).containsEntry("durationMinutes", 60);
    }

    @Test
    void accountSuspended_WhenBanCarriesNoReasonOrDuration_SubstitutesEmptyStringAndZero() {
        Map<String, Object> json = serialize(suspension(SuspensionType.BAN, null, null));

        assertThat(json).containsOnlyKeys("suspensionType", "reason", "durationMinutes");
        assertThat(json).containsEntry("suspensionType", "BAN");
        assertThat(json).containsEntry("reason", "");
        assertThat(json).containsEntry("durationMinutes", 0);
    }

    @Test
    void plannerPublished_WhenBroadcast_CarriesPlannerAndAuthorIdentity() {
        Map<String, Object> json = serialize(new PlannerPublishedPayload(
                PLANNER_ID.toString(), "Deck", "NAIVE", "ab12c"));

        assertThat(json).containsOnlyKeys("plannerId", "plannerTitle", "authorEpithet", "authorSuffix");
        assertThat(json).containsEntry("plannerId", PLANNER_ID.toString());
        assertThat(json).containsEntry("plannerTitle", "Deck");
        assertThat(json).containsEntry("authorEpithet", "NAIVE");
        assertThat(json).containsEntry("authorSuffix", "ab12c");
    }

    @Test
    void notification_WhenRichFieldsPresent_CarriesEveryField() {
        Map<String, Object> json = serialize(notification(
                "COMMENT_RECEIVED", "42", PLANNER_ID.toString(), "Deck", "nice", COMMENT_PUBLIC_ID.toString()));

        assertThat(json).containsOnlyKeys("id", "type", "contentId", "createdAt",
                "plannerId", "plannerTitle", "commentSnippet", "commentPublicId");
        assertThat(json).containsEntry("id", NOTIFICATION_PUBLIC_ID.toString());
        assertThat(json).containsEntry("type", "COMMENT_RECEIVED");
        assertThat(json).containsEntry("contentId", "42");
        assertThat(json).containsEntry("createdAt", "2026-03-04T05:06:07Z");
        assertThat(json).containsEntry("plannerId", PLANNER_ID.toString());
        assertThat(json).containsEntry("plannerTitle", "Deck");
        assertThat(json).containsEntry("commentSnippet", "nice");
        assertThat(json).containsEntry("commentPublicId", COMMENT_PUBLIC_ID.toString());
    }

    @Test
    void clientEvent_WhenBuiltFromEnvelope_CarriesRoutingFieldsWithoutFanOutExclusions() {
        SseEnvelope envelope = SseEnvelope.commentEvent(
                PLANNER_ID, SseEventType.COMMENT_ADDED, COMMENT_PUBLIC_ID.toString(), 42L,
                Map.of("id", COMMENT_PUBLIC_ID.toString()));

        Map<String, Object> json = serialize(ClientSseEvent.from(envelope));

        assertThat(json).containsOnlyKeys("type", "plannerId", "entityId", "payload");
        assertThat(json).containsEntry("type", "comment:added");
        assertThat(json).containsEntry("plannerId", PLANNER_ID.toString());
        assertThat(json).containsEntry("entityId", COMMENT_PUBLIC_ID.toString());
    }

    @Test
    void clientEvent_WhenUserTargeted_OmitsTheOriginatingDevice() {
        SseEnvelope envelope = SseEnvelope.userEvent(
                7L, SseEventType.COMMENT_ADDED, PLANNER_ID.toString(),
                "88888888-9999-4aaa-8bbb-cccccccccccc", Map.of("id", PLANNER_ID.toString()));

        Map<String, Object> json = serialize(ClientSseEvent.from(envelope));

        assertThat(json).containsOnlyKeys("type", "userId", "entityId", "payload");
        assertThat(json).containsEntry("userId", 7);
    }

    @Test
    void plannerPublished_WhenAuthorAccountGone_CarriesNullAuthorIdentity() {
        Map<String, Object> json = serialize(new PlannerPublishedPayload(
                PLANNER_ID.toString(), "Deck", null, null));

        assertThat(json).containsOnlyKeys("plannerId", "plannerTitle");
    }

    @Test
    void notification_WhenRichFieldsAbsent_OmitsThemRatherThanEmittingNull() {
        Map<String, Object> json = serialize(notification(
                "PLANNER_RECOMMENDED", PLANNER_ID.toString(), null, null, null, null));

        assertThat(json).containsOnlyKeys("id", "type", "contentId", "createdAt");
        assertThat(json).containsEntry("type", "PLANNER_RECOMMENDED");
        assertThat(json).containsEntry("contentId", PLANNER_ID.toString());
    }

    private static AccountSuspendedPayload suspension(
            SuspensionType type, String reason, Integer durationMinutes) {
        return AccountSuspendedPayload.of(type, reason, durationMinutes);
    }

    private static NotificationEventPayload notification(String type, String contentId, String plannerId,
            String plannerTitle, String commentSnippet, String commentPublicId) {
        return new NotificationEventPayload(
                NOTIFICATION_PUBLIC_ID.toString(), type, contentId, "2026-03-04T05:06:07Z",
                plannerId, plannerTitle, commentSnippet, commentPublicId);
    }

    private Map<String, Object> serialize(Object payload) {
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(payload), new TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("payload is not serializable", e);
        }
    }
}
