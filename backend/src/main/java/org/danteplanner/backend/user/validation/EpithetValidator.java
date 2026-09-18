package org.danteplanner.backend.user.validation;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EpithetValidator {

    private final EpithetConfig epithetConfig;

    public void requireValidEpithet(String epithet) {
        if (!epithetConfig.isValidEpithet(epithet)) {
            throw new InvalidRequestException("INVALID_EPITHET", "Invalid epithet: " + epithet);
        }
    }
}
