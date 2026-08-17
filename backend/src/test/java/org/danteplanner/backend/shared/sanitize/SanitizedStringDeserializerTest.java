package org.danteplanner.backend.shared.sanitize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The annotation's reach, on the record shape every request DTO uses: a declared component is
 * transformed on the way in, and an undeclared one beside it is not.
 */
class SanitizedStringDeserializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Stands in for a request DTO. A fixture rather than a real DTO because the point under test is
     * the annotation reaching a record component at all, which no production payload can isolate.
     */
    record Payload(
        @Sanitized(SanitizerKind.PLAIN)
        String plain,
        @Sanitized(SanitizerKind.PLANNER_CONTENT)
        String document,
        String untouched
    ) {}

    private static final String DANGEROUS_DOCUMENT = """
        {"sectionNotes":{"intro":{"content":{"type":"doc","content":[{"type":"text",\
        "text":"click","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}]}}}}""";

    @Test
    @DisplayName("a plain component arrives with its markup stripped")
    void plainComponent_WhenPayloadCarriesHtml_ArrivesAsText() throws Exception {
        Payload payload = MAPPER.readValue(
                MAPPER.writeValueAsString(new RawPayload(
                        "<script>alert('xss')</script><b>Hello world</b>", "{}", "")),
                Payload.class);

        assertThat(payload.plain()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("a component carrying no declaration is left exactly as it arrived")
    void undeclaredComponent_WhenPayloadCarriesHtml_ArrivesUnchanged() throws Exception {
        String raw = "<b>kept</b>";

        Payload payload = MAPPER.readValue(
                MAPPER.writeValueAsString(new RawPayload("", "{}", raw)),
                Payload.class);

        assertThat(payload.untouched()).isEqualTo(raw);
    }

    @Test
    @DisplayName("a planner-content component arrives with its dangerous link neutralized")
    void plannerContentComponent_WhenLinkUsesScriptProtocol_ArrivesNeutralized() throws Exception {
        Payload payload = MAPPER.readValue(
                MAPPER.writeValueAsString(new RawPayload("", DANGEROUS_DOCUMENT, "")),
                Payload.class);

        JsonNode document = MAPPER.readTree(payload.document());
        assertThat(document.at("/sectionNotes/intro/content/content/0/marks/0/attrs/href").asText())
                .isEqualTo("#");
    }

    @Test
    @DisplayName("a declared component that arrives null stays null")
    void declaredComponent_WhenPayloadOmitsIt_StaysNull() throws Exception {
        Payload payload = MAPPER.readValue("{}", Payload.class);

        assertThat(payload.plain()).isNull();
        assertThat(payload.document()).isNull();
    }

    /** The same shape without any declaration, used to write payloads the sanitizer has not seen. */
    record RawPayload(String plain, String document, String untouched) {}
}
