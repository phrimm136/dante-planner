package org.danteplanner.backend.shared.sanitize;

import java.io.IOException;
import java.io.Serial;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;

/**
 * Reads a String property through the {@link SanitizerKind} its {@link Sanitized} declaration names.
 *
 * <p>Bound only through the {@code @Sanitized} bundle, never registered for the String type at
 * large.</p>
 */
public class SanitizedStringDeserializer extends StdDeserializer<String>
        implements ContextualDeserializer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SanitizerKind kind;

    /**
     * Creates the unresolved instance Jackson instantiates from the annotation.
     */
    public SanitizedStringDeserializer() {
        this(null);
    }

    private SanitizedStringDeserializer(SanitizerKind kind) {
        super(String.class);
        this.kind = kind;
    }

    /**
     * Resolves the instance that reads a specific property, carrying that property's kind.
     *
     * <p>A null property means Jackson is resolving the deserializer outside any property (a root
     * value or a container's element type), where no declaration exists to read; the resolved
     * instance then reads the value without transforming it.</p>
     *
     * @param context  the active deserialization context
     * @param property the property about to be read, or null outside a property
     * @return a deserializer bound to the property's declared kind
     */
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        Sanitized declaration = property == null ? null : property.getAnnotation(Sanitized.class);
        return new SanitizedStringDeserializer(declaration == null ? null : declaration.value());
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = StringDeserializer.instance.deserialize(parser, context);
        return kind == null ? value : kind.apply(value);
    }
}
