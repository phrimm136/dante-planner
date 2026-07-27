package org.danteplanner.backend.util;
import org.danteplanner.backend.shared.util.HtmlSanitizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HtmlSanitizer.
 * Tests XSS attack vectors from OWASP cheat sheet.
 */
class HtmlSanitizerTest {

    @Test
    void sanitize_WhenScriptTag_RemovesTag() {
        String input = "<script>alert('xss')</script>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenScriptTagWithText_RemovesTagKeepsText() {
        String input = "Hello <script>alert('xss')</script> World";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("Hello World", result);
    }

    @Test
    void sanitize_WhenHtmlTags_RemovesTags() {
        String input = "Hello <b>bold</b> and <i>italic</i>!";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("Hello bold and italic!", result);
    }

    @Test
    void sanitize_WhenImgTag_RemovesTag() {
        String input = "<img src=x onerror=alert('xss')>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenIframeTag_RemovesTag() {
        String input = "<iframe src='javascript:alert(1)'></iframe>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenEventAttributes_RemovesAttributes() {
        String input = "<div onclick='alert(1)'>Click me</div>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("Click me", result);
    }

    @Test
    void sanitize_WhenStyleTag_RemovesTag() {
        String input = "<style>body{background:red}</style>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenPlainText_Unchanged() {
        String input = "Plain text without HTML";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    void sanitize_WhenNull_ReturnsNull() {
        String result = HtmlSanitizer.sanitize(null);
        assertNull(result);
    }

    @Test
    void sanitize_WhenEmptyString_ReturnsEmpty() {
        String result = HtmlSanitizer.sanitize("");
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenBlankString_ReturnsBlank() {
        String input = "   ";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    void sanitize_WhenSvgXss_RemovesTag() {
        String input = "<svg onload=alert(1)>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenObjectTag_RemovesTag() {
        String input = "<object data='javascript:alert(1)'>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenEmbedTag_RemovesTag() {
        String input = "<embed src='javascript:alert(1)'>";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals("", result);
    }

    @Test
    void sanitize_WhenHtmlEntities_PreservesAsText() {
        String input = "&lt;script&gt;alert(1)&lt;/script&gt;";
        String result = HtmlSanitizer.sanitize(input);
        // HTML entities are already safe text, Jsoup preserves them
        // This is correct: user typed literal "&lt;" not actual "<" tag
        assertEquals(input, result);
    }

    @Test
    void sanitize_WhenUnicodeCharacters_Preserved() {
        String input = "Hello 世界 🌍";
        String result = HtmlSanitizer.sanitize(input);
        assertEquals(input, result);
    }
}
