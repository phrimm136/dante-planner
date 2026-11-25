package org.danteplanner.backend.shared.ratelimit;

import java.util.function.Function;

import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;

/**
 * Every bucket key is spelled {@code subject + ":" + endpoint}. A live bucket lives under its key
 * in Redis, so respelling one grants every limited caller a fresh allowance.
 */
public enum RateLimitPolicy {

    CRUD(RateLimitProperties::crud, new Endpoint.CallerNamed(), Subject.USER, OnStoreFailure.ADMIT),

    SSE(RateLimitProperties::sse, new Endpoint.Fixed("sse"), Subject.USER, OnStoreFailure.ADMIT),

    COMMENT(RateLimitProperties::comment, new Endpoint.Fixed("comment"), Subject.USER, OnStoreFailure.ADMIT),

    REPORT(RateLimitProperties::report, new Endpoint.Fixed("report"), Subject.USER, OnStoreFailure.ADMIT),

    MODERATION(RateLimitProperties::moderation, new Endpoint.Fixed("moderation"), Subject.USER, OnStoreFailure.ADMIT),

    AUTH(RateLimitProperties::auth, new Endpoint.Fixed("auth"), Subject.CLIENT, OnStoreFailure.REFUSE),

    PLANNER_COMMENT_SSE(RateLimitProperties::sse, new Endpoint.Fixed("planner-comment-sse"), Subject.CLIENT, OnStoreFailure.ADMIT),

    PUBLIC_READ(RateLimitProperties::publicRead, new Endpoint.Fixed("public-read"), Subject.CLIENT, OnStoreFailure.ADMIT),

    PLANNER_STATS(RateLimitProperties::plannerStats, new Endpoint.Fixed("planner-stats"), Subject.CLIENT, OnStoreFailure.ADMIT),

    VIEW_RECORD(RateLimitProperties::viewRecord, new Endpoint.Fixed("view-record"), Subject.CLIENT, OnStoreFailure.ADMIT);

    public sealed interface Endpoint {

        record Fixed(String name) implements Endpoint {
        }

        record CallerNamed() implements Endpoint {
        }
    }

    public enum Subject {

        USER,

        CLIENT
    }

    public enum OnStoreFailure {

        REFUSE,

        ADMIT
    }

    private final Function<RateLimitProperties, BucketConfig> bucket;
    private final Endpoint endpoint;
    private final Subject subject;
    private final OnStoreFailure onStoreFailure;

    RateLimitPolicy(
            Function<RateLimitProperties, BucketConfig> bucket,
            Endpoint endpoint,
            Subject subject,
            OnStoreFailure onStoreFailure) {
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.subject = subject;
        this.onStoreFailure = onStoreFailure;
    }

    public Subject subject() {
        return subject;
    }

    public OnStoreFailure onStoreFailure() {
        return onStoreFailure;
    }

    public boolean requiresCallerNamedEndpoint() {
        return switch (endpoint) {
            case Endpoint.Fixed fixed -> false;
            case Endpoint.CallerNamed callerNamed -> true;
        };
    }

    public BucketConfig bucket(RateLimitProperties properties) {
        return bucket.apply(properties);
    }

    Endpoint endpoint() {
        return endpoint;
    }
}
