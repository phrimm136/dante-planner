package org.danteplanner.exclusivedbfixture;

import org.springframework.jdbc.core.JdbcTemplate;

import org.danteplanner.backend.integration.ExclusiveDatabaseSupport;

public abstract class ExclusiveMemberFixture extends ExclusiveDatabaseSupport {

    protected static void emptyPlannerTables(JdbcTemplate jdbc) {
        ExclusiveDatabaseSupport.emptyPlannerTables(jdbc);
    }

    void setUp(JdbcTemplate jdbc) {
        emptyPlannerTables(jdbc);
    }
}
