package org.danteplanner.backend.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitPolicy value();

    String endpoint() default "";

    RateLimitDenial denial() default RateLimitDenial.RESPOND;
}
