package org.danteplanner.backend.shared.sanitize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * <p>A bundled {@link JsonDeserialize} binds {@link SanitizedStringDeserializer} to the annotated
 * property alone.</p>
 *
 * <p>The compiler propagates a record-component annotation to the backing field and to the
 * canonical constructor parameter Jackson binds, while the component itself keeps the annotation
 * for reflection.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@JsonDeserialize(using = SanitizedStringDeserializer.class)
public @interface Sanitized {

    SanitizerKind value();
}
