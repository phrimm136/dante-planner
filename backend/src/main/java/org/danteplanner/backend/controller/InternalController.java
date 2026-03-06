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

    /**
     * Reload game data from static JSON files.
     *
     * <p>Triggers {@link GameDataRegistry#refresh()} so the backend
     * picks up new/changed data without a container restart.</p>
     */
    @PostMapping("/refresh-game-data")
    public ResponseEntity<Map<String, String>> refreshGameData(
            @RequestHeader("X-Internal-Api-Key") String providedKey) {

        if (apiKey.isEmpty() || !apiKey.equals(providedKey)) {
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
