package org.danteplanner.backend.shared.config;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.time.ZoneOffset;

@Component
public class EpithetConfig implements EpithetProvider {

    private static final Map<String, LocalDate> EPITHETS = Map.ofEntries(
            Map.entry("NAIVE", LocalDate.of(2026, 1, 21)),
            Map.entry("STUPID", LocalDate.of(2026, 1, 21)),
            Map.entry("RATIONAL", LocalDate.of(2026, 1, 21)),
            Map.entry("BRILLIANT", LocalDate.of(2026, 1, 21)),
            Map.entry("UNBENDING", LocalDate.of(2026, 1, 21)),
            Map.entry("PROACTIVE", LocalDate.of(2026, 1, 21)),
            Map.entry("RESOURCEFUL", LocalDate.of(2026, 1, 21)),
            Map.entry("AUGUST", LocalDate.of(2026, 1, 21)),
            Map.entry("DIGNIFIED", LocalDate.of(2026, 1, 21)),
            Map.entry("LOVELY", LocalDate.of(2026, 1, 21)),
            Map.entry("GUILEFUL", LocalDate.of(2026, 1, 21)),
            Map.entry("ASTUTE", LocalDate.of(2026, 1, 21)),
            Map.entry("INTELLIGENT", LocalDate.of(2026, 1, 21)),
            Map.entry("CURIOUS", LocalDate.of(2026, 1, 21)),
            Map.entry("FORSAKEN", LocalDate.of(2026, 1, 21)),
            Map.entry("ZEALOUS", LocalDate.of(2026, 1, 21)),
            Map.entry("METHODICAL", LocalDate.of(2026, 1, 21)),
            Map.entry("METICULOUS", LocalDate.of(2026, 1, 21)),
            Map.entry("DILIGENT", LocalDate.of(2026, 1, 21)),
            Map.entry("POETIC", LocalDate.of(2026, 1, 21)),
            Map.entry("ELEGANT", LocalDate.of(2026, 1, 21)),
            Map.entry("THOROUGH", LocalDate.of(2026, 1, 21)),
            Map.entry("ATTUNED", LocalDate.of(2026, 1, 21)),
            Map.entry("LOYAL", LocalDate.of(2026, 1, 21)),
            Map.entry("COMPOSED", LocalDate.of(2026, 1, 21)),
            Map.entry("BLIND", LocalDate.of(2026, 1, 21))
    );

    @Override
    public List<String> getEpithets() {
        return List.copyOf(EPITHETS.keySet());
    }

    @Override
    public int getWeight(String keyword) {
        return getWeight(keyword, LocalDate.now(ZoneOffset.UTC));
    }

    int getWeight(String keyword, LocalDate referenceDate) {
        return WeightDecay.weightOf(EPITHETS.get(keyword), referenceDate);
    }

    public boolean isValidEpithet(String keyword) {
        return EPITHETS.containsKey(keyword);
    }

}
