package org.danteplanner.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.shared.util.TiptapUrlSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TiptapUrlSanitizer.
 * Tests URL validation against OWASP dangerous protocols.
 */
class TiptapUrlSanitizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest(name = "href {0} becomes {1}")
    @CsvSource(delimiter = '|', value = {
        "javascript:alert('xss')                    | #",
        "data:text/html,<script>alert(1)</script>   | #",
        "vbscript:msgbox('xss')                     | #",
        "https://example.com                        | https://example.com",
        "/path/to/page                              | /path/to/page",
        "mailto:test@example.com                    | mailto:test@example.com",
        "tel:+1234567890                            | tel:+1234567890",
    })
    void sanitizeJson_WhenLinkHref_MatchesUrlPolicy(String href, String expected) throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "%s"}
                  }],
                  "text": "Link"
                }]
              }]
            }
            """.formatted(href);

        JsonNode root = mapper.readTree(TiptapUrlSanitizer.sanitizeJson(input));

        assertThat(root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText())
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "src {0} becomes {1}")
    @CsvSource(delimiter = '|', value = {
        "javascript:alert(1)                | #",
        "https://example.com/image.png      | https://example.com/image.png",
    })
    void sanitizeJson_WhenImageSrc_MatchesUrlPolicy(String src, String expected) throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "image",
                "attrs": {"src": "%s"}
              }]
            }
            """.formatted(src);

        JsonNode root = mapper.readTree(TiptapUrlSanitizer.sanitizeJson(input));

        assertThat(root.path("content").get(0).path("attrs").path("src").asText())
                .isEqualTo(expected);
    }

    @Test
    void sanitizeJson_WhenNullInput_ReturnsNull() {
        assertThat(TiptapUrlSanitizer.sanitizeJson(null)).isNull();
    }

    @Test
    void sanitizeJson_WhenEmptyString_ReturnsEmpty() {
        assertThat(TiptapUrlSanitizer.sanitizeJson("")).isEmpty();
    }

    @Test
    void sanitizeJson_WhenInvalidJson_ReturnsOriginal() {
        String input = "{invalid json}";

        assertThat(TiptapUrlSanitizer.sanitizeJson(input)).isEqualTo(input);
    }
}
