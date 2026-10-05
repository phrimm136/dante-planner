package org.danteplanner.exclusivedbfixture;

import org.springframework.jdbc.core.JdbcTemplate;

public class SharedNeighbourFixture {

    void setUp(JdbcTemplate jdbc) {
        ExclusiveMemberFixture.emptyPlannerTables(jdbc);
    }
}
