package org.danteplanner.backend.shared.sanitize;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.deser.jdk.StringDeserializer;

public class SanitizedStringDeserializer extends StdDeserializer<String> {

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
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        Sanitized declaration = property == null ? null : property.getAnnotation(Sanitized.class);
        return new SanitizedStringDeserializer(declaration == null ? null : declaration.value());
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        String value = StringDeserializer.instance.deserialize(parser, context);
        return kind == null ? value : kind.apply(value);
    }
}
