package org.danteplanner.backend.shared.ratelimit;

import java.util.function.Function;

import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;

/**
 * The complete set of rate-limited policies — the single answer to "what is rate limited".
 *
 * <p>Every bucket key is spelled {@code subject + ":" + endpoint}. The subject identifies the
 * caller (a user id, a pre-auth client identifier, or a device id carrying the policy's
 * {@code subjectPrefix}); the endpoint is either fixed by the policy or named per call, which is
 * how one bucket family covers many routes.</p>
 *
 * <p>Those keys are a production contract: a live bucket lives under its key in Redis, so
 * respelling one silently grants every limited caller a fresh allowance.
 * {@code RateLimitKeyFormatTest} pins each spelling, and fails for any policy it does not
 * cover.</p>
 */
public enum RateLimitPolicy {

    /** Ordinary write/read traffic; the endpoint is named per call, one bucket per route. */
    CRUD(RateLimitProperties::getCrud, null, "", Subject.USER),

    IMPORT(RateLimitProperties::getImportConfig, "import", "", Subject.USER),

    SSE(RateLimitProperties::getSse, "sse", "", Subject.USER),

    COMMENT(RateLimitProperties::getComment, "comment", "", Subject.USER),

    REPORT(RateLimitProperties::getReport, "report", "", Subject.USER),

    MODERATION(RateLimitProperties::getModeration, "moderation", "", Subject.USER),

    /** Reachable before a user exists, so the subject is a client identifier ({@code ip:} / {@code device:}). */
    AUTH(RateLimitProperties::getAuth, "auth", "", Subject.CLIENT),

    /** Guests may subscribe, so the subject is a client identifier ({@code ip:} / {@code device:}). */
    PLANNER_COMMENT_SSE(RateLimitProperties::getSse, "planner-comment-sse", "", Subject.CLIENT),

    /** Unauthenticated reads of published content, so the subject is a client identifier. */
    PUBLIC_READ(RateLimitProperties::getPublicRead, "public-read", "", Subject.CLIENT);

    /** What a policy's buckets are keyed by, and therefore what a charger must resolve first. */
    public enum Subject {

        /** The authenticated account; the policy's endpoints are all behind authentication. */
        USER,

        /** A pre-authentication client identifier ({@code ip:} / {@code device:}). */
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

    /**
     * What identifies the caller this policy charges.
     *
     * @return the subject kind
     */
    public Subject subject() {
        return subject;
    }

    /**
     * Whether a charger must name the endpoint, because this policy spreads one bucket family
     * across many routes rather than naming a single endpoint of its own.
     *
     * @return true when the endpoint comes from the caller
     */
    public boolean requiresCallerNamedEndpoint() {
        return endpoint == null;
    }

    BucketConfig bucket(RateLimitProperties properties) {
        return bucket.apply(properties);
    }

    /** The endpoint label baked into the key and the 429 body, or null when the caller names it. */
    String endpoint() {
        return endpoint;
    }

    String subjectPrefix() {
        return subjectPrefix;
    }
}
