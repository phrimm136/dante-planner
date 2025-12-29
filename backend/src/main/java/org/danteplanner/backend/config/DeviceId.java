package org.danteplanner.backend.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for injecting device ID from HTTP-only cookie.
 *
 * <p>When used on a controller method parameter, the {@link DeviceIdArgumentResolver}
 * will automatically extract the device ID from the request cookie, or generate
 * and set a new one if not present.</p>
 *
 * @see DeviceIdArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeviceId {
}
