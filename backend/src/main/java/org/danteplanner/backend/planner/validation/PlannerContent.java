package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ego.Ego;
import org.danteplanner.backend.planner.validation.floor.Floors;
import org.danteplanner.backend.planner.validation.identity.Identity;
import org.danteplanner.backend.planner.validation.skill.SkillPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PlannerContent(
        List<String> selectedKeywords,
        List<Long> selectedBuffIds,
        String selectedGiftKeyword,
        List<String> selectedGiftIds,
        List<String> observationGiftIds,
        List<String> comprehensiveGiftIds,
        Map<String, Equipment> equipment,
        List<Long> deploymentOrder,
        Map<String, SkillPlan> skillEAState,
        Floors floorSelections,
        Map<String, JsonNode> sectionNotes) {

    public record Equipment(Identity identity, Map<String, Ego> egos) {
    }

    public Map<String, Identity> identities() {
        return bySinner(Equipment::identity);
    }

    public Map<String, Map<String, Ego>> egos() {
        return bySinner(Equipment::egos);
    }

    private <T> Map<String, T> bySinner(Function<Equipment, T> part) {
        return equipment.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, sinner -> part.apply(sinner.getValue()),
                        (first, later) -> first, LinkedHashMap::new));
    }
}
