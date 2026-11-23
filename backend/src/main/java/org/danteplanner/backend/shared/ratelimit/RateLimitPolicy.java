package org.danteplanner.backend.shared.ratelimit;

import java.util.function.Function;

import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;

/**
 * Every bucket key is spelled {@code subject + ":" + endpoint}. A live bucket lives under its key
 * in Redis, so respelling one grants every limited caller a fresh allowance.
 */
public enum RateLimitPolicy {

    CRUD(RateLimitProperties::crud, new Endpoint.CallerNamed(), "", Subject.USER, false),

    SSE(RateLimitProperties::sse, new Endpoint.Fixed("sse"), "", Subject.USER, false),

    COMMENT(RateLimitProperties::comment, new Endpoint.Fixed("comment"), "", Subject.USER, false),

    REPORT(RateLimitProperties::report, new Endpoint.Fixed("report"), "", Subject.USER, false),

    MODERATION(RateLimitProperties::moderation, new Endpoint.Fixed("moderation"), "", Subject.USER, false),

    AUTH(RateLimitProperties::auth, new Endpoint.Fixed("auth"), "", Subject.CLIENT, true),

    PLANNER_COMMENT_SSE(RateLimitProperties::sse, new Endpoint.Fixed("planner-comment-sse"), "", Subject.CLIENT, false),

    PUBLIC_READ(RateLimitProperties::publicRead, new Endpoint.Fixed("public-read"), "", Subject.CLIENT, false),

    PLANNER_STATS(RateLimitProperties::plannerStats, new Endpoint.Fixed("planner-stats"), "", Subject.CLIENT, false),

    VIEW_RECORD(RateLimitProperties::viewRecord, new Endpoint.Fixed("view-record"), "", Subject.CLIENT, false);

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

    private final Function<RateLimitProperties, BucketConfig> bucket;
    private final Endpoint endpoint;
    private final String subjectPrefix;
    private final Subject subject;
    private final boolean failClosed;

    RateLimitPolicy(
            Function<RateLimitProperties, BucketConfig> bucket,
            Endpoint endpoint,
            String subjectPrefix,
            Subject subject,
            boolean failClosed) {
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.subjectPrefix = subjectPrefix;
        this.subject = subject;
        this.failClosed = failClosed;
    }

    public Subject subject() {
        return subject;
    }

    public boolean failsClosed() {
        return failClosed;
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

    String subjectPrefix() {
        return subjectPrefix;
    }
}
