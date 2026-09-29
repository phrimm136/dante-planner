package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public final class ContentFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ContentPath EQUIPMENT = ContentPath.of("equipment");
    private static final ContentPath SKILL_EA_STATE = ContentPath.of("skillEAState");

    private ContentFixtures() {
    }

    public static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    public static List<SinnerEntry> equipmentOfSinnerFive(String sinnerEquipment) {
        return List.of(new SinnerEntry("05", EQUIPMENT.key("05"), json(sinnerEquipment)));
    }

    public static List<SinnerEntry> skillsOfSinnerFive(String slots) {
        return List.of(new SinnerEntry("05", SKILL_EA_STATE.key("05"), json(slots)));
    }
}
