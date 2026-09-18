package org.danteplanner.backend.shared.sanitize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Holds for an identifier, an enum name, or another value the server itself checks against a
 * closed set before use — never for a value a caller may fill with arbitrary text.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface NotUserContent {
}
