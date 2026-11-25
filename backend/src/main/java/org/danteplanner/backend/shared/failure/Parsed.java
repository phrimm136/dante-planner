package org.danteplanner.backend.shared.failure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a type whose value exists only when its check passed: only its own static factory creates it. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Parsed {
}
