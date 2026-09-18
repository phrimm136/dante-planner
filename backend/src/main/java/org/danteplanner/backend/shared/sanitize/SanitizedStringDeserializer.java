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

public class SanitizedStringDeserializer extends StdDeserializer<String>
        implements ContextualDeserializer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SanitizerKind kind;

    public SanitizedStringDeserializer() {
        this(null);
    }

    private SanitizedStringDeserializer(SanitizerKind kind) {
        super(String.class);
        this.kind = kind;
    }

    /**
     * A null property means Jackson is resolving the deserializer outside any property (a root
     * value or a container's element type), where no declaration exists to read.
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
