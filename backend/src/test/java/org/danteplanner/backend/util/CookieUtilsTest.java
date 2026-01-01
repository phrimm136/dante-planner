package org.danteplanner.backend.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CookieUtils.
 *
 * <p>Tests cookie creation, retrieval, and clearing
 * with proper security attributes.</p>
 */
class CookieUtilsTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private CookieUtils cookieUtils;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Default: secure cookies enabled (production mode)
        cookieUtils = new CookieUtils(true);
    }

    @Nested
    @DisplayName("setCookie Tests")
    class SetCookieTests {

        @Test
        @DisplayName("Should set HttpOnly, Secure, and SameSite attributes")
        void setCookie_setsHttpOnlySecureSameSite() {
            // Arrange
            String name = "accessToken";
            String value = "jwt.token.value";
            int maxAge = 900;

            // Act
            cookieUtils.setCookie(response, name, value, maxAge);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            Cookie cookie = cookieCaptor.getValue();
            assertEquals(name, cookie.getName());
            assertEquals(value, cookie.getValue());
            assertTrue(cookie.isHttpOnly(), "Cookie should be HttpOnly");
            assertTrue(cookie.getSecure(), "Cookie should be Secure");
            assertEquals("/", cookie.getPath());
            assertEquals(maxAge, cookie.getMaxAge());
            assertEquals("Strict", cookie.getAttribute("SameSite"));
        }

        @Test
        @DisplayName("Should set path to root")
        void setCookie_setsPathToRoot() {
            // Act
            cookieUtils.setCookie(response, "test", "value", 3600);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertEquals("/", cookieCaptor.getValue().getPath());
        }

        @Test
        @DisplayName("Should set correct max age")
        void setCookie_setsCorrectMaxAge() {
            // Arrange
            int expectedMaxAge = 86400; // 1 day

            // Act
            cookieUtils.setCookie(response, "test", "value", expectedMaxAge);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertEquals(expectedMaxAge, cookieCaptor.getValue().getMaxAge());
        }

        @Test
        @DisplayName("Should set Secure=false when configured for development")
        void setCookie_setsSecureFalseInDevMode() {
            // Arrange - dev mode with secure cookies disabled
            CookieUtils devCookieUtils = new CookieUtils(false);

            // Act
            devCookieUtils.setCookie(response, "test", "value", 3600);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertFalse(cookieCaptor.getValue().getSecure(), "Cookie should not be Secure in dev mode");
        }
    }

    @Nested
    @DisplayName("getCookieValue Tests")
    class GetCookieValueTests {

        @Test
        @DisplayName("Should return value for existing cookie")
        void getCookieValue_returnsValueForExistingCookie() {
            // Arrange
            String expectedName = "accessToken";
            String expectedValue = "jwt.token.value";
            Cookie[] cookies = {
                    new Cookie("otherCookie", "otherValue"),
                    new Cookie(expectedName, expectedValue),
                    new Cookie("anotherCookie", "anotherValue")
            };
            when(request.getCookies()).thenReturn(cookies);

            // Act
            String result = cookieUtils.getCookieValue(request, expectedName);

            // Assert
            assertEquals(expectedValue, result);
        }

        @Test
        @DisplayName("Should return null for missing cookie")
        void getCookieValue_returnsNullForMissingCookie() {
            // Arrange
            Cookie[] cookies = {
                    new Cookie("otherCookie", "otherValue"),
                    new Cookie("anotherCookie", "anotherValue")
            };
            when(request.getCookies()).thenReturn(cookies);

            // Act
            String result = cookieUtils.getCookieValue(request, "nonExistentCookie");

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when no cookies present")
        void getCookieValue_returnsNullWhenNoCookies() {
            // Arrange
            when(request.getCookies()).thenReturn(null);

            // Act
            String result = cookieUtils.getCookieValue(request, "anyCookie");

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null for empty cookies array")
        void getCookieValue_returnsNullForEmptyCookiesArray() {
            // Arrange
            when(request.getCookies()).thenReturn(new Cookie[0]);

            // Act
            String result = cookieUtils.getCookieValue(request, "anyCookie");

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle case-sensitive cookie names")
        void getCookieValue_isCaseSensitive() {
            // Arrange
            Cookie[] cookies = {new Cookie("AccessToken", "value")};
            when(request.getCookies()).thenReturn(cookies);

            // Act
            String result = cookieUtils.getCookieValue(request, "accessToken");

            // Assert - different case should not match
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("clearCookie Tests")
    class ClearCookieTests {

        @Test
        @DisplayName("Should set cookie with zero max age")
        void clearCookie_setsCookieWithZeroMaxAge() {
            // Arrange
            String name = "accessToken";

            // Act
            cookieUtils.clearCookie(response, name);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            Cookie cookie = cookieCaptor.getValue();
            assertEquals(name, cookie.getName());
            assertNull(cookie.getValue());
            assertEquals(0, cookie.getMaxAge());
        }

        @Test
        @DisplayName("Should set security attributes when clearing")
        void clearCookie_setsSecurityAttributes() {
            // Act
            cookieUtils.clearCookie(response, "tokenToClear");

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            Cookie cookie = cookieCaptor.getValue();
            assertTrue(cookie.isHttpOnly(), "Cleared cookie should be HttpOnly");
            assertTrue(cookie.getSecure(), "Cleared cookie should be Secure");
            assertEquals("/", cookie.getPath());
            assertEquals("Strict", cookie.getAttribute("SameSite"));
        }

        @Test
        @DisplayName("Should set path to root when clearing")
        void clearCookie_setsPathToRoot() {
            // Act
            cookieUtils.clearCookie(response, "test");

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertEquals("/", cookieCaptor.getValue().getPath());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty cookie name")
        void setCookie_throwsForEmptyName() {
            // Act & Assert - Cookie API does not allow empty names
            assertThrows(
                    IllegalArgumentException.class,
                    () -> cookieUtils.setCookie(response, "", "value", 3600)
            );
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null cookie name")
        void setCookie_throwsForNullName() {
            // Act & Assert - Cookie API does not allow null names
            assertThrows(
                    IllegalArgumentException.class,
                    () -> cookieUtils.setCookie(response, null, "value", 3600)
            );
        }

        @Test
        @DisplayName("Should handle empty cookie value")
        void setCookie_handlesEmptyValue() {
            // Act
            cookieUtils.setCookie(response, "name", "", 3600);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            assertEquals("", cookieCaptor.getValue().getValue());
        }

        @Test
        @DisplayName("Should handle zero max age")
        void setCookie_handlesZeroMaxAge() {
            // Act
            cookieUtils.setCookie(response, "name", "value", 0);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            assertEquals(0, cookieCaptor.getValue().getMaxAge());
        }

        @Test
        @DisplayName("Should handle negative max age")
        void setCookie_handlesNegativeMaxAge() {
            // Act - negative max age means session cookie
            cookieUtils.setCookie(response, "name", "value", -1);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            assertEquals(-1, cookieCaptor.getValue().getMaxAge());
        }
    }
}
