package org.danteplanner.backend.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a request handler is deliberately charged against no rate-limit policy.
 *
 * <p>Coverage requires every handler to carry a declaration, so an exemption is stated rather than
 * left to the absence of {@link RateLimited}: an unguarded endpoint and a decision not to guard it
 * are otherwise the same text.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimitExempt {
}
