package org.danteplanner.backend.shared.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum SseEventType {
    COMMENT_ADDED("comment:added", false, UserDelivery.EMITTERS),
    NOTIFY_COMMENT("notify:comment", true, UserDelivery.EMITTERS),
    NOTIFY_PUBLISHED("notify:published", true, UserDelivery.EMITTERS),
    NOTIFY_RECOMMENDED("notify:recommended", true, UserDelivery.EMITTERS),
    SETTINGS_INVALIDATED("settings:invalidated", false, UserDelivery.SETTINGS_CACHE),
    ACCOUNT_SUSPENDED("account_suspended", true, UserDelivery.SUSPENSION_NOTICE);

    public enum UserDelivery {
        EMITTERS,
        SETTINGS_CACHE,
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

    public UserDelivery userDelivery() {
        return userDelivery;
    }

    public boolean deliversRawPayload() {
        return rawPayloadDelivery;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    private static final Map<String, SseEventType> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(SseEventType::getValue, Function.identity()));

    public static SseEventType fromValue(String value) {
        return BY_VALUE.get(value);
    }
}
