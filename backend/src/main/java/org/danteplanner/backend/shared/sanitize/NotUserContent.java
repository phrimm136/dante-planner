package org.danteplanner.backend.shared.sanitize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a request-DTO String property carries no user-authored text, and is the only
 * opt-out the sanitization coverage guard accepts in place of {@link Sanitized}.
 *
 * <p>It holds for an identifier, an enum name, or another value the server itself checks against a
 * closed set before use — never for a value a caller may fill with arbitrary text.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface NotUserContent {
}
