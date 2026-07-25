package org.danteplanner.backend.shared.service;

import java.util.function.Function;

import org.danteplanner.backend.shared.config.RateLimitProperties;
import org.danteplanner.backend.shared.config.RateLimitProperties.BucketConfig;

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
    CRUD(RateLimitProperties::getCrud, null, ""),

    IMPORT(RateLimitProperties::getImportConfig, "import", ""),

    SSE(RateLimitProperties::getSse, "sse", ""),

    COMMENT(RateLimitProperties::getComment, "comment", ""),

    REPORT(RateLimitProperties::getReport, "report", ""),

    MODERATION(RateLimitProperties::getModeration, "moderation", ""),

    /** Reachable before a user exists, so the subject is a client identifier ({@code ip:} / {@code device:}). */
    AUTH(RateLimitProperties::getAuth, "auth", ""),

    /** Guests may subscribe, so the subject is the device rather than a user id. */
    PLANNER_COMMENT_SSE(RateLimitProperties::getSse, "planner-comment-sse", "device:");

    private final Function<RateLimitProperties, BucketConfig> bucket;
    private final String endpoint;
    private final String subjectPrefix;

    RateLimitPolicy(
            Function<RateLimitProperties, BucketConfig> bucket,
            String endpoint,
            String subjectPrefix) {
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.subjectPrefix = subjectPrefix;
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
