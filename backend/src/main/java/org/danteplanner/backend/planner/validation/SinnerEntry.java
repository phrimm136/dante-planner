package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

public record SinnerEntry(String sinnerKey, ContentPath path, JsonNode value) {
}
