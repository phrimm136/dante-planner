package org.danteplanner.backend.user.validation;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * Whether an account may take a username epithet.
 *
 * <p>The accepted set is loaded configuration rather than a fixed list, so the rule cannot be
 * expressed as a constraint on the request DTO.</p>
 */
@Component
@RequiredArgsConstructor
public class EpithetValidator {

    private final EpithetConfig epithetConfig;

    /**
     * Require the epithet to be one the configured set offers.
     *
     * @param epithet the requested epithet
     * @throws InvalidRequestException if the epithet is not on offer
     */
    public void requireValidEpithet(String epithet) {
        if (!epithetConfig.isValidEpithet(epithet)) {
            throw new InvalidRequestException("INVALID_EPITHET", "Invalid epithet: " + epithet);
        }
    }
}
