package org.danteplanner.backend.user.validation;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EpithetValidator {

    private final EpithetConfig epithetConfig;

    @Parsed
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Epithet {

        private final String value;

        private static Epithet of(String value, EpithetConfig epithets) {
            if (!epithets.isValidEpithet(value)) {
                throw new InvalidRequestException("INVALID_EPITHET", "Invalid epithet: " + value);
            }
            return new Epithet(value);
        }
    }

    public Epithet requireValidEpithet(String epithet) {
        return Epithet.of(epithet, epithetConfig);
    }
}
