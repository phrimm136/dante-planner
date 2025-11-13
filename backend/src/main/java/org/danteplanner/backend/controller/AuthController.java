package org.danteplanner.backend.controller;

import org.danteplanner.backend.dto.LoginResponse;
import org.danteplanner.backend.dto.OAuthCallbackRequest;
import org.danteplanner.backend.dto.UserDto;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.service.GoogleOAuthService;
import org.danteplanner.backend.service.JwtService;
import org.danteplanner.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GoogleOAuthService googleOAuthService;
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(GoogleOAuthService googleOAuthService, UserService userService, JwtService jwtService) {
        this.googleOAuthService = googleOAuthService;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/google/callback")
    public ResponseEntity<LoginResponse> googleCallback(@RequestBody OAuthCallbackRequest request) {
        try {
            String redirectUri = "http://localhost:5173/auth/callback/google";

            Map<String, String> tokens = googleOAuthService.exchangeCodeForToken(request.getCode(), redirectUri);
            String accessToken = tokens.get("access_token");

            Map<String, String> userInfo = googleOAuthService.getUserInfo(accessToken);

            User user = userService.findOrCreateUser("google", userInfo);

            String jwtAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
            String jwtRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

            UserDto userDto = userService.toDto(user);

            LoginResponse response = LoginResponse.builder()
                    .accessToken(jwtAccessToken)
                    .refreshToken(jwtRefreshToken)
                    .user(userDto)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/apple/callback")
    public ResponseEntity<LoginResponse> appleCallback(@RequestBody OAuthCallbackRequest request) {
        return ResponseEntity.status(501).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            Long userId = jwtService.getUserIdFromToken(token);

            User user = userService.findById(userId);
            UserDto userDto = userService.toDto(user);

            return ResponseEntity.ok(userDto);
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}
