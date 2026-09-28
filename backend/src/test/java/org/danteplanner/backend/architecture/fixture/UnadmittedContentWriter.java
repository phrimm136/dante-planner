package org.danteplanner.backend.architecture.fixture;

import org.danteplanner.backend.planner.entity.PlannerContent;

public class UnadmittedContentWriter {

    public void store(PlannerContent row, String content) {
        row.setContent(content);
    }
}
