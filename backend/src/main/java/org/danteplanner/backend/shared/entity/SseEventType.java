package org.danteplanner.backend.shared.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum SseEventType {
    COMMENT_ADDED("comment:added", PayloadDelivery.ENVELOPE, UserDelivery.EMITTERS),
    NOTIFY_COMMENT("notify:comment", PayloadDelivery.RAW, UserDelivery.EMITTERS),
    NOTIFY_PUBLISHED("notify:published", PayloadDelivery.RAW, UserDelivery.EMITTERS),
    NOTIFY_RECOMMENDED("notify:recommended", PayloadDelivery.RAW, UserDelivery.EMITTERS),
    SETTINGS_INVALIDATED("settings:invalidated", PayloadDelivery.ENVELOPE, UserDelivery.SETTINGS_CACHE),
    ACCOUNT_SUSPENDED("account_suspended", PayloadDelivery.RAW, UserDelivery.SUSPENSION_NOTICE);

    public enum UserDelivery {
        EMITTERS,
        SETTINGS_CACHE,
        SUSPENSION_NOTICE
    }

    public enum PayloadDelivery {
        RAW,
        ENVELOPE
    }

    private final String value;
    private final PayloadDelivery payloadDelivery;
    private final UserDelivery userDelivery;

    SseEventType(String value, PayloadDelivery payloadDelivery, UserDelivery userDelivery) {
        this.value = value;
        this.payloadDelivery = payloadDelivery;
        this.userDelivery = userDelivery;
    }

    public UserDelivery userDelivery() {
        return userDelivery;
    }

    public PayloadDelivery payloadDelivery() {
        return payloadDelivery;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    private static final Map<String, SseEventType> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(SseEventType::getValue, Function.identity()));

    public sealed interface Lookup {

        record Known(SseEventType type) implements Lookup {
        }

        record Unknown() implements Lookup {
        }
    }

    public static Lookup lookup(String value) {
        SseEventType type = BY_VALUE.get(value);
        return type == null ? new Lookup.Unknown() : new Lookup.Known(type);
    }
}
