package org.danteplanner.backend.planner.validation.floor;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable.CategoryFloors;

import java.util.List;

public record Floors(CategoryFloors layout, List<FloorSelection> counted, List<JsonNode> uncounted) {

    public Floors {
        counted = List.copyOf(counted);
        uncounted = List.copyOf(uncounted);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static Floors of(List<JsonNode> floorSelections, @JacksonInject CategoryFloors layout) {
        int counted = Math.min(floorSelections.size(), layout.floorCount());
        return new Floors(layout,
                floorSelections.subList(0, counted).stream().map(FloorBoundary::read).toList(),
                floorSelections.subList(counted, floorSelections.size()));
    }

    public FloorBoundary.Parsed parsed() {
        return new FloorBoundary.Parsed(layout,
                counted.stream().<ParsedFloor>map(ParsedFloor.Accepted::new).toList(), List.of());
    }
}
