package org.danteplanner.backend.shared.ratelimit;

import java.util.function.Function;

import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;

/**
 * Every bucket key is spelled {@code subject + ":" + endpoint}. A live bucket lives under its key
 * in Redis, so respelling one grants every limited caller a fresh allowance.
 */
public enum RateLimitPolicy {

    CRUD(RateLimitProperties::getCrud, null, "", Subject.USER, false),

    SSE(RateLimitProperties::getSse, "sse", "", Subject.USER, false),

    COMMENT(RateLimitProperties::getComment, "comment", "", Subject.USER, false),

    REPORT(RateLimitProperties::getReport, "report", "", Subject.USER, false),

    MODERATION(RateLimitProperties::getModeration, "moderation", "", Subject.USER, false),

    AUTH(RateLimitProperties::getAuth, "auth", "", Subject.CLIENT, true),

    PLANNER_COMMENT_SSE(RateLimitProperties::getSse, "planner-comment-sse", "", Subject.CLIENT, false),

    PUBLIC_READ(RateLimitProperties::getPublicRead, "public-read", "", Subject.CLIENT, false);

    public enum Subject {

        USER,

        CLIENT
    }

    private final Function<RateLimitProperties, BucketConfig> bucket;
    private final String endpoint;
    private final String subjectPrefix;
    private final Subject subject;
    private final boolean failClosed;

    RateLimitPolicy(
            Function<RateLimitProperties, BucketConfig> bucket,
            String endpoint,
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
        return endpoint == null;
    }

    BucketConfig bucket(RateLimitProperties properties) {
        return bucket.apply(properties);
    }

    String endpoint() {
        return endpoint;
    }

    String subjectPrefix() {
        return subjectPrefix;
    }
}
