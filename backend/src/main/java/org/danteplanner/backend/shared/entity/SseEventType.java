package org.danteplanner.backend.shared.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Server-Sent Event type enum.
 * The wire value (the channel name the frontend listens on) is preserved exactly
 * via {@link #getValue()}; a typo now fails to compile instead of silently dropping a client event.
 */
public enum SseEventType {
    COMMENT_ADDED("comment:added", false, UserDelivery.EMITTERS),
    NOTIFY_COMMENT("notify:comment", true, UserDelivery.EMITTERS),
    NOTIFY_PUBLISHED("notify:published", true, UserDelivery.EMITTERS),
    NOTIFY_RECOMMENDED("notify:recommended", true, UserDelivery.EMITTERS),
    SETTINGS_INVALIDATED("settings:invalidated", false, UserDelivery.SETTINGS_CACHE),
    ACCOUNT_SUSPENDED("account_suspended", true, UserDelivery.SUSPENSION_NOTICE);

    /**
     * What a subscriber does with an event that arrives on the user channel.
     *
     * <p>Most events reach the user's own emitters. The two exceptions mutate this node's session
     * state instead, so the subscriber dispatches on this rather than on the event type — a new
     * event type then picks a delivery here rather than falling through a chain of equality tests.
     */
    public enum UserDelivery {
        /** Send to the user's connected emitters. */
        EMITTERS,
        /** Drop this node's cached settings for the user. */
        SETTINGS_CACHE,
        /** Tell the user's emitters their account was suspended. */
        SUSPENSION_NOTICE
    }

    private final String value;
    private final boolean rawPayloadDelivery;
    private final UserDelivery userDelivery;

    SseEventType(String value, boolean rawPayloadDelivery, UserDelivery userDelivery) {
        this.value = value;
        this.rawPayloadDelivery = rawPayloadDelivery;
        this.userDelivery = userDelivery;
    }

    /**
     * @return what a subscriber does with this event on the user channel
     */
    public UserDelivery userDelivery() {
        return userDelivery;
    }

    /**
     * Whether clients receive this event's payload directly rather than the fan-out envelope.
     *
     * <p>Comment-stream and control events carry the envelope, because the client reads its routing
     * fields alongside the payload. Notification-style events predate the envelope on the wire and
     * their client schemas require the payload's own fields at the top level, so the envelope stays
     * server-side.</p>
     */
    public boolean deliversRawPayload() {
        return rawPayloadDelivery;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    private static final Map<String, SseEventType> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(SseEventType::getValue, Function.identity()));

    /**
     * Resolve the type a wire value names.
     *
     * @param value the wire value carried on the event
     * @return the matching type, or null when no type carries that value
     */
    public static SseEventType fromValue(String value) {
        return BY_VALUE.get(value);
    }
}
