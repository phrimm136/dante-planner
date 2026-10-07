package org.danteplanner.backend.util;
import org.danteplanner.backend.shared.util.CookieUtils;

import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CookieUtils.
 *
 * <p>Tests cookie creation, retrieval, and clearing
 * with proper security attributes.</p>
 */
@ExtendWith(MockitoExtension.class)
class CookieUtilsTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private CookieUtils cookieUtils;

    @BeforeEach
    void setUp() {
        // Default: secure cookies enabled (production mode), same-origin, SameSite=Lax
        cookieUtils = new CookieUtils(true, "", "Lax");
    }

    @Nested
    class SetCookieTests {

        @Test
        void setCookie_WhenCalled_SetsHttpOnlySecureSameSite() {
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
            assertThat(cookie.getName()).isEqualTo(name);
            assertThat(cookie.getValue()).isEqualTo(value);
            assertThat(cookie.isHttpOnly()).as("Cookie should be HttpOnly").isTrue();
            assertThat(cookie.getSecure()).as("Cookie should be Secure").isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getMaxAge()).isEqualTo(maxAge);
            assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        }

        @Test
        void setCookie_WhenCalled_SetsPathToRoot() {
            // Act
            cookieUtils.setCookie(response, "test", "value", 3600);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertThat(cookieCaptor.getValue().getPath()).isEqualTo("/");
        }

        @Test
        void setCookie_WhenCalled_SetsCorrectMaxAge() {
            // Arrange
            int expectedMaxAge = 86400; // 1 day

            // Act
            cookieUtils.setCookie(response, "test", "value", expectedMaxAge);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertThat(cookieCaptor.getValue().getMaxAge()).isEqualTo(expectedMaxAge);
        }

        @Test
        void setCookie_WhenDevMode_SetsSecureFalse() {
            // Arrange - dev mode with secure cookies disabled
            CookieUtils devCookieUtils = new CookieUtils(false, "", "Lax");

            // Act
            devCookieUtils.setCookie(response, "test", "value", 3600);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertThat(cookieCaptor.getValue().getSecure()).as("Cookie should not be Secure in dev mode").isFalse();
        }
    }

    @Nested
    class GetCookieValueTests {

        @Test
        void getCookieValue_WhenCookieExists_ReturnsValue() {
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
            Optional<String> result = cookieUtils.getCookieValue(request, expectedName);

            // Assert
            assertThat(result).isEqualTo(Optional.of(expectedValue));
        }

        @Test
        void getCookieValue_WhenCookieMissing_ReturnsEmpty() {
            // Arrange
            Cookie[] cookies = {
                    new Cookie("otherCookie", "otherValue"),
                    new Cookie("anotherCookie", "anotherValue")
            };
            when(request.getCookies()).thenReturn(cookies);

            // Act
            Optional<String> result = cookieUtils.getCookieValue(request, "nonExistentCookie");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void getCookieValue_WhenNoCookies_ReturnsEmpty() {
            // Arrange
            when(request.getCookies()).thenReturn(null);

            // Act
            Optional<String> result = cookieUtils.getCookieValue(request, "anyCookie");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void getCookieValue_WhenEmptyCookiesArray_ReturnsEmpty() {
            // Arrange
            when(request.getCookies()).thenReturn(new Cookie[0]);

            // Act
            Optional<String> result = cookieUtils.getCookieValue(request, "anyCookie");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void getCookieValue_WhenDifferentCase_ReturnsEmpty() {
            // Arrange
            Cookie[] cookies = {new Cookie("AccessToken", "value")};
            when(request.getCookies()).thenReturn(cookies);

            // Act
            Optional<String> result = cookieUtils.getCookieValue(request, "accessToken");

            // Assert - different case should not match
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class ClearCookieTests {

        @Test
        void clearCookie_WhenCalled_SetsZeroMaxAge() {
            // Arrange
            String name = "accessToken";

            // Act
            cookieUtils.clearCookie(response, name);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            Cookie cookie = cookieCaptor.getValue();
            assertThat(cookie.getName()).isEqualTo(name);
            assertThat(cookie.getValue()).isEqualTo("");
            assertThat(cookie.getMaxAge()).isEqualTo(0);
        }

        @Test
        void clearCookie_WhenCalled_SetsSecurityAttributes() {
            // Act
            cookieUtils.clearCookie(response, "tokenToClear");

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            Cookie cookie = cookieCaptor.getValue();
            assertThat(cookie.isHttpOnly()).as("Cleared cookie should be HttpOnly").isTrue();
            assertThat(cookie.getSecure()).as("Cleared cookie should be Secure").isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        }

        @Test
        void clearCookie_WhenCalled_EmitsDeletingSetCookieHeader() {
            // A cleared cookie only takes effect through its rendered header, and the Cookie
            // object alone cannot show that: the container decides what an empty value emits.
            cookieUtils.clearCookie(response, "accessToken");

            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertThat(new Rfc6265CookieProcessor().generateHeader(cookieCaptor.getValue(), null))
                    .isEqualTo("accessToken=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/;"
                            + " Secure; HttpOnly; SameSite=Lax");
        }

        @Test
        void clearCookie_WhenCalled_SetsPathToRoot() {
            // Act
            cookieUtils.clearCookie(response, "test");

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            assertThat(cookieCaptor.getValue().getPath()).isEqualTo("/");
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void setCookie_WhenEmptyName_Throws() {
            // Act & Assert - Cookie API does not allow empty names
            assertThatThrownBy(() -> cookieUtils.setCookie(response, "", "value", 3600))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cookie name may not be null or zero length");
        }

        @Test
        void setCookie_WhenNullName_Throws() {
            // Act & Assert - Cookie API does not allow null names
            assertThatThrownBy(() -> cookieUtils.setCookie(response, null, "value", 3600))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cookie name may not be null or zero length");
        }

        @Test
        void setCookie_WhenEmptyValue_SetsEmptyValue() {
            // Act
            cookieUtils.setCookie(response, "name", "", 3600);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            assertThat(cookieCaptor.getValue().getValue()).isEqualTo("");
        }

        @Test
        void setCookie_WhenZeroMaxAge_SetsZeroMaxAge() {
            // Act
            cookieUtils.setCookie(response, "name", "value", 0);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            assertThat(cookieCaptor.getValue().getMaxAge()).isEqualTo(0);
        }

        @Test
        void setCookie_WhenNegativeMaxAge_SetsNegativeMaxAge() {
            // Act - negative max age means session cookie
            cookieUtils.setCookie(response, "name", "value", -1);

            // Assert
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            assertThat(cookieCaptor.getValue().getMaxAge()).isEqualTo(-1);
        }
    }
}
