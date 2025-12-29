package org.danteplanner.backend.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.LoginResponse;
import org.danteplanner.backend.dto.OAuthCallbackRequest;
import org.danteplanner.backend.dto.RefreshTokenRequest;
import org.danteplanner.backend.dto.UserDto;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.service.GoogleOAuthService;
import org.danteplanner.backend.service.JwtService;
import org.danteplanner.backend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final GoogleOAuthService googleOAuthService;
    private final UserService userService;
    private final JwtService jwtService;

    @Value("${oauth.google.redirect-uri}")
    private String googleRedirectUri;

    @PostMapping("/google/callback")
    public ResponseEntity<UserDto> googleCallback(
            @Valid @RequestBody OAuthCallbackRequest request,
            HttpServletResponse response) {
        log.info("Processing Google OAuth callback with PKCE");

        // Exchange code for tokens with PKCE code_verifier
        Map<String, String> tokens = googleOAuthService.exchangeCodeForToken(
                request.getCode(),
                googleRedirectUri,
                request.getCodeVerifier()
        );
        String accessToken = tokens.get("access_token");

        Map<String, String> userInfo = googleOAuthService.getUserInfo(accessToken);

        User user = userService.findOrCreateUser("google", userInfo);

        String jwtAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String jwtRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        // Set HttpOnly cookies for tokens
        setCookie(response, "accessToken", jwtAccessToken, 15 * 60); // 15 minutes
        setCookie(response, "refreshToken", jwtRefreshToken, 7 * 24 * 60 * 60); // 7 days

        UserDto userDto = userService.toDto(user);

        log.info("User logged in successfully: {}", user.getEmail());
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/apple/callback")
    public ResponseEntity<LoginResponse> appleCallback(@RequestBody OAuthCallbackRequest request) {
        return ResponseEntity.status(501).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(HttpServletRequest request) {
        String token = getTokenFromCookie(request, "accessToken");

        if (token == null) {
            return ResponseEntity.status(401).build();
        }

        Long userId = jwtService.getUserIdFromToken(token);

        User user = userService.findById(userId);
        UserDto userDto = userService.toDto(user);

        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/refresh")
    public ResponseEntity<UserDto> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info("Processing token refresh");

        String refreshToken = getTokenFromCookie(request, "refreshToken");

        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }

        // Validate refresh token type
        Claims claims = jwtService.validateToken(refreshToken);
        String tokenType = claims.get("type", String.class);

        if (!"refresh".equals(tokenType)) {
            log.warn("Invalid token type for refresh: {}", tokenType);
            return ResponseEntity.status(401).build();
        }

        Long userId = claims.get("userId", Long.class);

        User user = userService.findById(userId);

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        // Update cookies with new tokens
        setCookie(response, "accessToken", newAccessToken, 15 * 60); // 15 minutes
        setCookie(response, "refreshToken", newRefreshToken, 7 * 24 * 60 * 60); // 7 days

        UserDto userDto = userService.toDto(user);

        log.info("Token refreshed successfully for user: {}", user.getEmail());
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        log.info("Processing logout");

        // Clear cookies
        clearCookie(response, "accessToken");
        clearCookie(response, "refreshToken");

        return ResponseEntity.noContent().build();
    }

    // Helper methods for cookie management
    private void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // HTTPS only
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String getTokenFromCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
