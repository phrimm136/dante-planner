package org.danteplanner.backend.shared.sanitize;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.deser.jdk.StringDeserializer;

public class SanitizedStringDeserializer extends StdDeserializer<String> {

    private sealed interface SanitizerContext {

        record Contextless() implements SanitizerContext {
        }

        record WithKind(SanitizerKind kind) implements SanitizerContext {
        }
    }

    private final SanitizerContext sanitizer;

    public SanitizedStringDeserializer() {
        this(new SanitizerContext.Contextless());
    }

    private SanitizedStringDeserializer(SanitizerContext sanitizer) {
        super(String.class);
        this.sanitizer = sanitizer;
    }

    /**
     * A null property means Jackson is resolving the deserializer outside any property (a root
     * value or a container's element type), where no declaration exists to read.
     */
    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        Sanitized declaration = property == null ? null : property.getAnnotation(Sanitized.class);
        return new SanitizedStringDeserializer(declaration == null
                ? new SanitizerContext.Contextless()
                : new SanitizerContext.WithKind(declaration.value()));
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        String value = StringDeserializer.instance.deserialize(parser, context);
        return switch (sanitizer) {
            case SanitizerContext.Contextless contextless -> value;
            case SanitizerContext.WithKind withKind -> withKind.kind().apply(value);
        };
    }
}
