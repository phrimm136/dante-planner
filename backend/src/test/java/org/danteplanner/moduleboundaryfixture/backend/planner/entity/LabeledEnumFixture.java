package org.danteplanner.moduleboundaryfixture.backend.planner.entity;

import org.danteplanner.moduleboundaryfixture.backend.shared.LabelFixture;

public enum LabeledEnumFixture implements LabelFixture {
    PLAIN,
    WITH_BODY {
        @Override
        public String toString() {
            return "body";
        }
    }
}
