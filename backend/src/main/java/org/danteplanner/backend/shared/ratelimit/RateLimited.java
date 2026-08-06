package org.danteplanner.backend.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.danteplanner.backend.shared.service.RateLimitPolicy;

/**
 * Declares the rate-limit policy a request handler is charged against.
 *
 * <p>A class-level declaration covers every handler the class declares; a method-level declaration
 * overrides it. The charge happens before the handler runs.</p>
 *
 * <p>Attachment is decoupled from bucket identity: the policy name, not the annotated class or
 * method, decides which live Redis bucket the request draws from, so moving a handler cannot
 * reset a limit.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /** The policy whose bucket the request is charged against. */
    RateLimitPolicy value();

    /**
     * The route label separating this bucket from the other buckets of a policy that keys its
     * buckets by a caller-named endpoint. Empty where the policy names its own endpoint.
     */
    String endpoint() default "";
}
