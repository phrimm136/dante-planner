package org.danteplanner.fixture.moduleboundary.backend.planner.entity;

import org.danteplanner.fixture.moduleboundary.backend.shared.LabelFixture;

public enum LabeledEnumFixture implements LabelFixture {
    PLAIN,
    WITH_BODY {
        @Override
        public String toString() {
            return "body";
        }
    }
}
