package org.danteplanner.backend.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HtmlSanitizer.
 * Tests XSS attack vectors from OWASP cheat sheet.
 */
class HtmlSanitizerTest {

    @Test
    void sanitize_scriptTag_removesTag() {
        String input = "<script>alert('xss')</script>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_scriptTagWithText_removesTagKeepsText() {
        String input = "Hello <script>alert('xss')</script> World";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("Hello  World", result);
    }

    @Test
    void sanitize_htmlTags_removesTags() {
        String input = "Hello <b>bold</b> and <i>italic</i>!";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("Hello bold and italic!", result);
    }

    @Test
    void sanitize_imgTag_removesTag() {
        String input = "<img src=x onerror=alert('xss')>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_iframeTag_removesTag() {
        String input = "<iframe src='javascript:alert(1)'></iframe>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_onEventAttributes_removesAttributes() {
        String input = "<div onclick='alert(1)'>Click me</div>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("Click me", result);
    }

    @Test
    void sanitize_styleTag_removesTag() {
        String input = "<style>body{background:red}</style>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_plainText_unchanged() {
        String input = "Plain text without HTML";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    void sanitize_null_returnsNull() {
        String result = HtmlSanitizer.sanitize(null);
        assertNull(result);
    }

    @Test
    void sanitize_emptyString_returnsEmpty() {
        String result = HtmlSanitizer.sanitize("");
        assertEquals("", result);
    }

    @Test
    void sanitize_blankString_returnsBlank() {
        String input = "   ";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    void sanitize_svgXss_removesTag() {
        String input = "<svg onload=alert(1)>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_objectTag_removesTag() {
        String input = "<object data='javascript:alert(1)'>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_embedTag_removesTag() {
        String input = "<embed src='javascript:alert(1)'>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_htmlEntities_preservesAsText() {
        String input = "&lt;script&gt;alert(1)&lt;/script&gt;";
        String result = HtmlSanitizer.sanitize(input);
        // HTML entities are already safe text, Jsoup preserves them
        // This is correct: user typed literal "&lt;" not actual "<" tag
        assertEquals(input, result);
    }

    @Test
    void sanitize_unicodeCharacters_preserved() {
        String input = "Hello 世界 🌍";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals(input, result);
    }
}
