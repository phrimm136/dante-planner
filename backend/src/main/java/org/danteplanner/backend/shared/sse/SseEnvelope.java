package org.danteplanner.backend.shared.sse;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonValue;
import org.danteplanner.backend.shared.security.Viewer;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.shared.entity.SseEventType;

public sealed interface SseEnvelope {

    SseEventType type();

    @JsonValue
    default Wire wire() {
        return Wire.of(this);
    }

    record UserEvent(Long userId, SseEventType type, String entityId, Object payload)
            implements SseEnvelope {
    }

    record SettingsInvalidation(Long userId) implements SseEnvelope {

        @Override
        public SseEventType type() {
            return SseEventType.SETTINGS_INVALIDATED;
        }
    }

    record CommentEvent(UUID plannerId, SseEventType type, String entityId, UserExclusion exclusion, Object payload)
            implements SseEnvelope {
    }

    record Broadcast(UserExclusion exclusion, SseEventType type, Object payload) implements SseEnvelope {
    }

    record AccountSuspended(Long userId, Object payload) implements SseEnvelope {

        @Override
        public SseEventType type() {
            return SseEventType.ACCOUNT_SUSPENDED;
        }
    }

    sealed interface UserExclusion {

        default String describe() {
            return switch (this) {
                case None none -> "null";
                case User user -> user.userId().toString();
            };
        }

        record None() implements UserExclusion {
        }

        record User(Long userId) implements UserExclusion {
        }

        default boolean excludes(Viewer viewer) {
            return switch (this) {
                case None none -> false;
                case User user -> viewer.is(user.userId());
            };
        }

        default boolean excludes(Long userId) {
            return switch (this) {
                case None none -> false;
                case User user -> user.userId().equals(userId);
            };
        }
    }

    record Wire(
            SseEventType type,
            @Nullable Long userId,
            @Nullable String plannerId,
            @Nullable String entityId,
            @Nullable Long excludeUserId,
            @Nullable Object payload
    ) {

        static Wire of(SseEnvelope envelope) {
            return switch (envelope) {
                case UserEvent event -> new Wire(event.type(), event.userId(), null, event.entityId(),
                        null, event.payload());
                case SettingsInvalidation invalidation ->
                        new Wire(invalidation.type(), invalidation.userId(), null, null, null, null);
                case CommentEvent event -> new Wire(event.type(), null, event.plannerId().toString(),
                        event.entityId(), excludedUser(event.exclusion()), event.payload());
                case Broadcast broadcast -> new Wire(broadcast.type(), null, null, null,
                        excludedUser(broadcast.exclusion()), broadcast.payload());
                case AccountSuspended suspended ->
                        new Wire(suspended.type(), suspended.userId(), null, null, null, suspended.payload());
            };
        }

        public SseEnvelope on(SseChannel channel) {
            return switch (channel) {
                case COMMENT -> new CommentEvent(UUID.fromString(required(plannerId, "plannerId")), type,
                        required(entityId, "entityId"), userExclusion(),
                        required(payload, "payload"));
                case BROADCAST -> new Broadcast(userExclusion(), type, required(payload, "payload"));
                case USER -> switch (type.userDelivery()) {
                    case SETTINGS_CACHE -> new SettingsInvalidation(required(userId, "userId"));
                    case SUSPENSION_NOTICE ->
                            new AccountSuspended(required(userId, "userId"), required(payload, "payload"));
                    case EMITTERS -> new UserEvent(required(userId, "userId"), type, required(entityId, "entityId"),
                            required(payload, "payload"));
                };
            };
        }

        private static @Nullable Long excludedUser(UserExclusion exclusion) {
            return switch (exclusion) {
                case UserExclusion.None none -> null;
                case UserExclusion.User user -> user.userId();
            };
        }

        private UserExclusion userExclusion() {
            return excludeUserId == null ? new UserExclusion.None() : new UserExclusion.User(excludeUserId);
        }

        private <T> T required(@Nullable T slot, String name) {
            if (slot == null) {
                throw new IllegalArgumentException(type + " envelope on its channel carries no " + name);
            }
            return slot;
        }
    }
}
