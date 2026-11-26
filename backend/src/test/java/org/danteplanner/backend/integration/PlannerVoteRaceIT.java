package org.danteplanner.backend.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerVoteRaceIT extends SharedMySqlContainerSupport {

    private static final int ROUNDS = 20;

    @Autowired private UserRepository userRepository;
    @Autowired private PlannerRepository plannerRepository;
    @Autowired private PlannerStatsRepository statsRepository;
    @Autowired private PlannerCatalogService catalogService;
    @Autowired private PlannerEngagementService engagementService;
    @Autowired private DataSource dataSource;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private JdbcTemplate jdbc;
    private User author;
    private User voter;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        author = TestDataFactory.createTestUser(userRepository, "planner-vote-race-author@example.com");
        voter = TestDataFactory.createTestUser(userRepository, "planner-vote-race-voter@example.com");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void castVote_WhenOneUserVotesTwiceAtOnce_StoresOneVoteCountsOneAndRefusesTheOther() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID planner = TestDataFactory.planner(author).savePublished(plannerRepository, statsRepository,
                    catalogService).id();

            assertThat(twiceAtOnce(planner)).singleElement().isInstanceOf(VoteAlreadyExistsException.class);

            assertThat(voters(planner)).containsExactly(voter.getId());
            assertThat(upvotes(planner)).isEqualTo(1);
        }
    }

    private List<Throwable> twiceAtOnce(UUID planner) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        List<Future<Throwable>> running = new ArrayList<>();
        for (int writer = 0; writer < 2; writer++) {
            running.add(executor.submit(() -> {
                Hang.await(start, "both voters ready");
                return failureOf(() -> engagementService.castVote(voter.getId(), planner, VoteType.UP));
            }));
        }
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> writer : running) {
            Throwable failure = Hang.get(writer, "a voter finishing");
            if (failure != null) {
                failures.add(failure);
            }
        }
        return failures;
    }

    private static Throwable failureOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private List<Long> voters(UUID planner) {
        return jdbc.queryForList("SELECT user_id FROM planner_votes WHERE planner_id = UUID_TO_BIN(?)",
                Long.class, planner.toString());
    }

    private Integer upvotes(UUID planner) {
        return jdbc.queryForObject("SELECT upvotes FROM planner_stats WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, planner.toString());
    }
}
