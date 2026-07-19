package org.danteplanner.backend.integration;

import java.util.UUID;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.StatsReadsFlag;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stats cutover read seam: with divergent legacy and planner_stats counters, the flag selects
 * which store the served counter fields come from — planner_stats when on, legacy when off.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerStatsCutoverIT extends SharedMySqlContainerSupport {

    private static final int LEGACY_VIEWS = 5;
    private static final int STATS_VIEWS = 99;

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_stats_cutover_it");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StatsReadsFlag statsReadsFlag;

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        statsReadsFlag.setEnabled(false);
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        User owner = TestDataFactory.createTestUser(userRepository, "cutover-owner@example.com");
        plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId();
        jdbcTemplate.update("UPDATE planners SET view_count = ? WHERE id = UUID_TO_BIN(?)",
                LEGACY_VIEWS, plannerId.toString());
        jdbcTemplate.update(
                "INSERT INTO planner_stats (planner_id, view_count, upvotes) VALUES (UUID_TO_BIN(?), ?, ?)",
                plannerId.toString(), STATS_VIEWS, 0);
    }

    @Test
    @DisplayName("statsRead_WhenFlagOn_ServesStatsValues")
    void statsRead_WhenFlagOn_ServesStatsValues() throws Exception {
        statsReadsFlag.setEnabled(true);

        mockMvc.perform(get("/api/planner/md/published/{id}", plannerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(STATS_VIEWS));
    }

    @Test
    @DisplayName("statsRead_WhenFlagOff_ServesLegacyValues")
    void statsRead_WhenFlagOff_ServesLegacyValues() throws Exception {
        statsReadsFlag.setEnabled(false);

        mockMvc.perform(get("/api/planner/md/published/{id}", plannerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(LEGACY_VIEWS));
    }
}
