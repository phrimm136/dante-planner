package org.danteplanner.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.validation.GameDataRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Internal endpoints for CI/CD automation.
 *
 * <p>Secured by API key (not JWT). Intended to be called from
 * the same host via SSM or docker exec.</p>
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalController {

    private final GameDataRegistry gameDataRegistry;

    @Value("${internal.api-key:}")
    private String apiKey;

    @PostConstruct
    void validateApiKey() {
        if (apiKey.isBlank()) {
            log.warn("internal.api-key is not configured — /api/internal endpoints will reject all requests");
        }
    }

    /**
     * Reload game data from static JSON files.
     *
     * <p>Triggers {@link GameDataRegistry#refresh()} so the backend
     * picks up new/changed data without a container restart.</p>
     */
    @PostMapping("/refresh-game-data")
    public ResponseEntity<Map<String, String>> refreshGameData(
            @RequestHeader("X-Internal-Api-Key") String providedKey) {

        if (apiKey.isBlank() || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Unauthorized refresh-game-data attempt");
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Invalid API key"));
        }

        log.info("Refreshing game data via internal API");
        gameDataRegistry.refresh();

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "populated", String.valueOf(gameDataRegistry.isPopulated())
        ));
    }
}
