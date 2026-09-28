package org.danteplanner.backend.planner.floor;

import org.danteplanner.backend.planner.validation.FloorRuleTable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FloorRuleNamesTest {

    @Test
    @DisplayName("The module implements exactly the rules the table names")
    void ruleNames_WhenComparedWithTheTable_AreTheSameSet() {
        assertThat(FloorRules.ruleNames()).isEqualTo(FloorRuleTable.RULE_NAMES);
    }
}
