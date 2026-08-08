package org.danteplanner.backend.shared.sanitize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Declares that a request-DTO String property carries user content and names how it is sanitized.
 *
 * <p>The bundled {@link JsonDeserialize} binds {@link SanitizedStringDeserializer} to the annotated
 * property alone, so the transformation reaches exactly the properties that asked for it and no
 * globally registered String deserializer exists to reach the rest.</p>
 *
 * <p>The record-component target is what makes the declaration visible to both Jackson and the
 * coverage guard: the compiler propagates it to the backing field and to the canonical constructor
 * parameter Jackson binds, while the component itself keeps the annotation for reflection.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@JsonDeserialize(using = SanitizedStringDeserializer.class)
public @interface Sanitized {

    /**
     * The transformation applied to this property's incoming value.
     *
     * @return the sanitizer kind
     */
    SanitizerKind value();
}
