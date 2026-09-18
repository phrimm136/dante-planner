package org.danteplanner.backend.planner.entity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PlannerKeywords {

    public static final Set<String> VALID_KEYWORDS = Set.of(
            "Combustion", "Laceration", "Vibration", "Burst", "Sinking", "Breath", "Charge",
            "Slash", "Penetrate", "Hit",
            "CRIMSON", "SCARLET", "AMBER", "SHAMROCK", "AZURE", "INDIGO", "VIOLET",
            "9154",
            "Assemble", "KnowledgeExplored", "AaCePcBt", "SwordPlayOfTheHomeland",
            "EchoOfMansion", "TimeSuspend", "EmergencyChargeForceField", "BloodDinner",
            "BlackCloud", "RetaliationBook", "HeishouSynergy",
            "Bullet", "BlessingOfIndexPrescriptAlly", "Inspire",
            "9828", "SojiRyoshuEntangle", "DawnTeam"
    );

    private static final Map<String, String> RENAME_MAP = Map.of(
            "AccelBullet", "9828",
            "ChargeLoad", "EmergencyChargeForceField"
    );

    private final Set<String> keywords;
    private final Set<String> dropped;

    private PlannerKeywords(Set<String> keywords, Set<String> dropped) {
        this.keywords = Collections.unmodifiableSet(keywords);
        this.dropped = Collections.unmodifiableSet(dropped);
    }

    public static PlannerKeywords fromClient(Collection<String> raw) {
        return normalize(raw);
    }

    public static PlannerKeywords fromStorage(Collection<String> stored) {
        return normalize(stored);
    }

    private static PlannerKeywords normalize(Collection<String> raw) {
        if (raw == null) {
            return new PlannerKeywords(Set.of(), Set.of());
        }
        Set<String> remapped = raw.stream()
                .filter(k -> k != null && !k.isEmpty())
                .map(k -> RENAME_MAP.getOrDefault(k, k))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> dropped = remapped.stream()
                .filter(k -> !VALID_KEYWORDS.contains(k))
                .collect(Collectors.toSet());
        remapped.retainAll(VALID_KEYWORDS);
        return new PlannerKeywords(remapped, dropped);
    }

    public Set<String> asSet() {
        return new LinkedHashSet<>(keywords);
    }

    public Set<String> dropped() {
        return dropped;
    }

    public boolean isEmpty() {
        return keywords.isEmpty();
    }
}
