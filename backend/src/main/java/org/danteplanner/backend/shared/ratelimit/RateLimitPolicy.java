package org.danteplanner.backend.shared.ratelimit;

import java.util.function.Function;

import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;

/**
 * Every bucket key is spelled {@code subject + ":" + endpoint}. A live bucket lives under its key
 * in Redis, so respelling one grants every limited caller a fresh allowance.
 */
public enum RateLimitPolicy {

    CRUD(RateLimitProperties::getCrud, null, "", Subject.USER),

    IMPORT(RateLimitProperties::getImportConfig, "import", "", Subject.USER),

    SSE(RateLimitProperties::getSse, "sse", "", Subject.USER),

    COMMENT(RateLimitProperties::getComment, "comment", "", Subject.USER),

    REPORT(RateLimitProperties::getReport, "report", "", Subject.USER),

    MODERATION(RateLimitProperties::getModeration, "moderation", "", Subject.USER),

    AUTH(RateLimitProperties::getAuth, "auth", "", Subject.CLIENT),

    PLANNER_COMMENT_SSE(RateLimitProperties::getSse, "planner-comment-sse", "", Subject.CLIENT),

    PUBLIC_READ(RateLimitProperties::getPublicRead, "public-read", "", Subject.CLIENT);

    public enum Subject {

        USER,

        CLIENT
    }

    private final Function<RateLimitProperties, BucketConfig> bucket;
    private final String endpoint;
    private final String subjectPrefix;
    private final Subject subject;

    RateLimitPolicy(
            Function<RateLimitProperties, BucketConfig> bucket,
            String endpoint,
            String subjectPrefix,
            Subject subject) {
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.subjectPrefix = subjectPrefix;
        this.subject = subject;
    }

    public Subject subject() {
        return subject;
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
