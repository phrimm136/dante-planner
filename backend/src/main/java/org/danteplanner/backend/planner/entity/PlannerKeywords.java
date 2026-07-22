package org.danteplanner.backend.planner.entity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Planner keyword set as a domain value: membership in the authoritative
 * keyword list and legacy-name normalization live here, not in the
 * persistence adapter. {@link #fromClient} normalizes a client-sent set,
 * dropping unknowns rather than rejecting (a stale client must still sync);
 * {@link #fromStorage} is total — whatever was persisted reads back as the
 * current valid subset, never an error.
 */
public final class PlannerKeywords {

    /**
     * Valid planner keywords; members outside this list are dropped.
     */
    public static final Set<String> VALID_KEYWORDS = Set.of(
            // Status effects
            "Combustion", "Laceration", "Vibration", "Burst", "Sinking", "Breath", "Charge",
            // Attack types
            "Slash", "Penetrate", "Hit",
            // Affinities
            "CRIMSON", "SCARLET", "AMBER", "SHAMROCK", "AZURE", "INDIGO", "VIOLET",
            // Ego gifts
            "9154",
            // Synergy keywords
            "Assemble", "KnowledgeExplored", "AaCePcBt", "SwordPlayOfTheHomeland",
            "EchoOfMansion", "TimeSuspend", "EmergencyChargeForceField", "BloodDinner",
            "BlackCloud", "RetaliationBook", "HeishouSynergy",
            "Bullet", "BlessingOfIndexPrescriptAlly", "Inspire",
            "9828", "SojiRyoshuEntangle", "DawnTeam"
    );

    /**
     * Legacy keyword aliases from pre-rename clients, mapped to their current ids.
     * Renamed via migrations V038 (ChargeLoad) and V044 (AccelBullet); an outdated
     * client still syncing the old name must be normalized before storage. Keys are
     * intentionally absent from VALID_KEYWORDS.
     */
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

    /**
     * Normalize a client-sent keyword set: remap renamed ids, keep valid
     * members, collect the rest as {@link #dropped()} for the caller to surface.
     */
    public static PlannerKeywords fromClient(Collection<String> raw) {
        return normalize(raw);
    }

    /**
     * Read a persisted keyword collection. Total: unknown members (including
     * values written by code predating a rename) normalize or drop silently.
     */
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

    /**
     * The normalized keyword set (mutable copy, the shape entities carry).
     */
    public Set<String> asSet() {
        return new LinkedHashSet<>(keywords);
    }

    /**
     * Members that did not survive normalization, for logging by adapters.
     */
    public Set<String> dropped() {
        return dropped;
    }

    public boolean isEmpty() {
        return keywords.isEmpty();
    }
}
