package org.danteplanner.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TiptapUrlSanitizer.
 * Tests URL validation against OWASP dangerous protocols.
 */
class TiptapUrlSanitizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sanitizeJson_javascriptProtocol_replacesWithPlaceholder() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "javascript:alert('xss')"}
                  }],
                  "text": "Click me"
                }]
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String href = root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText();

        assertEquals("#", href);
    }

    @Test
    void sanitizeJson_dataProtocol_replacesWithPlaceholder() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "data:text/html,<script>alert(1)</script>"}
                  }],
                  "text": "Link"
                }]
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String href = root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText();

        assertEquals("#", href);
    }

    @Test
    void sanitizeJson_vbscriptProtocol_replacesWithPlaceholder() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "vbscript:msgbox('xss')"}
                  }],
                  "text": "Link"
                }]
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String href = root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText();

        assertEquals("#", href);
    }

    @Test
    void sanitizeJson_httpsUrl_preserved() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "https://example.com"}
                  }],
                  "text": "Safe link"
                }]
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String href = root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText();

        assertEquals("https://example.com", href);
    }

    @Test
    void sanitizeJson_relativeUrl_preserved() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "/path/to/page"}
                  }],
                  "text": "Relative link"
                }]
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String href = root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText();

        assertEquals("/path/to/page", href);
    }

    @Test
    void sanitizeJson_imageSrcJavascript_replacesWithPlaceholder() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "image",
                "attrs": {"src": "javascript:alert(1)"}
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String src = root.path("content").get(0)
                .path("attrs").path("src").asText();

        assertEquals("#", src);
    }

    @Test
    void sanitizeJson_imageSrcHttps_preserved() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "image",
                "attrs": {"src": "https://example.com/image.png"}
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String src = root.path("content").get(0)
                .path("attrs").path("src").asText();

        assertEquals("https://example.com/image.png", src);
    }

    @Test
    void sanitizeJson_nullInput_returnsNull() {
        String result = TiptapUrlSanitizer.sanitizeJson(null);
        assertNull(result);
    }

    @Test
    void sanitizeJson_emptyString_returnsEmpty() {
        String result = TiptapUrlSanitizer.sanitizeJson("");
        assertEquals("", result);
    }

    @Test
    void sanitizeJson_invalidJson_returnsOriginal() {
        String input = "{invalid json}";
        String result = TiptapUrlSanitizer.sanitizeJson(input);
        assertEquals(input, result);
    }

    @Test
    void sanitizeJson_mailtoUrl_preserved() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "mailto:test@example.com"}
                  }],
                  "text": "Email"
                }]
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String href = root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText();

        assertEquals("mailto:test@example.com", href);
    }

    @Test
    void sanitizeJson_telUrl_preserved() throws Exception {
        String input = """
            {
              "type": "doc",
              "content": [{
                "type": "paragraph",
                "content": [{
                  "type": "text",
                  "marks": [{
                    "type": "link",
                    "attrs": {"href": "tel:+1234567890"}
                  }],
                  "text": "Call"
                }]
              }]
            }
            """;

        String result = TiptapUrlSanitizer.sanitizeJson(input);
        JsonNode root = mapper.readTree(result);
        String href = root.path("content").get(0)
                .path("content").get(0)
                .path("marks").get(0)
                .path("attrs").path("href").asText();

        assertEquals("tel:+1234567890", href);
    }
}
