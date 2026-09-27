package org.danteplanner.backend.planner.validation;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class PlannerPublishValidator {

    public void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
        }
    }

    public void requireKnownKeywords(String content) {
        Set<String> unknown = PlannerKeywords.fromContent(content).dropped();
        if (!unknown.isEmpty()) {
            log.warn("Publish refused: unknown keywords {}", unknown);
            throw ValidationErrors.keywordInvalid(unknown);
        }
    }
}
