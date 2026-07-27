package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.service.CommentModerationService;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;

/**
 * Counter isolation: planner_stats is the single home of view/upvote/comment
 * counters — maintained on every transition and the source every read serves.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerStatsIsolationIT {

    /**
     * Its own database, and so its own context: these assert what the view recorder has buffered
     * versus flushed, and the recorder is one bean per context. A neighbour recording or flushing
     * moves the number this test is about to read.
     */
    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "stats_isolation");
    }




    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @Autowired
    private CommentCommandService commentCommandService;

    @Autowired
    private CommentModerationService commentModerationService;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerViewRecorder viewRecorder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private User commenter;
    private User admin;
    private Planner planner;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "stats-owner@example.com");
        commenter = TestDataFactory.createTestUser(userRepository, "stats-commenter@example.com");
        admin = TestDataFactory.createAdmin(userRepository, "stats-admin@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        catalogService.add(planner);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    private void seedStats(int viewCount, int upvotes, int commentCount) {
        statsRepository.deleteById(planner.getId());
        statsRepository.flush();
        statsRepository.save(PlannerStats.builder()
                .plannerId(planner.getId())
                .viewCount(viewCount)
                .upvotes(upvotes)
                .commentCount(commentCount)
                .build());
    }

    private int commentCount() {
        return statsRepository.findById(planner.getId())
                .map(PlannerStats::getCommentCount)
                .orElse(-1);
    }

    @Test
    @DisplayName("comment-count-maintained: create/reply increment, author and moderator deletes decrement, floor at zero")
    void commentCountMaintained_WhenCreateAndDeleteTransitions_CounterTracks() {
        seedStats(0, 0, 0);

        UUID topLevelId = commentCommandService.createComment(planner.getId(), commenter.getId(), null,
                new CreateCommentRequest("first", null)).id();
        assertThat(commentCount()).as("top-level comment increments").isEqualTo(1);

        UUID replyId = commentCommandService.createReply(topLevelId, owner.getId(), null, "a reply").id();
        assertThat(commentCount()).as("reply increments").isEqualTo(2);

        commentCommandService.deleteComment(replyId, owner.getId());
        assertThat(commentCount()).as("author delete decrements").isEqualTo(1);

        commentCommandService.deleteComment(replyId, owner.getId());
        assertThat(commentCount()).as("repeated delete is idempotent (already deleted)").isEqualTo(1);

        commentModerationService.deleteCommentByPublicId(admin.getId(), topLevelId, "cleanup");
        assertThat(commentCount()).as("moderator delete decrements").isEqualTo(0);

        commentModerationService.deleteCommentByPublicId(admin.getId(), topLevelId, "cleanup again");
        assertThat(commentCount()).as("idempotent moderator delete does not decrement").isEqualTo(0);

        // Floor: a live comment row the counter never accounted for (drift) must
        // not push the counter below zero on delete
        PlannerComment drifted = commentRepository.save(
                new PlannerComment(planner.getId(), commenter.getId(), "drifted", null, 0));
        commentCommandService.deleteComment(drifted.getPublicId(), commenter.getId());
        assertThat(commentCount()).as("decrement floors at zero").isEqualTo(0);
    }

    @Test
    @DisplayName("counter-reads-from-stats: list and detail serve seeded stats values, not derived counts")
    void counterReadsFromStats_WhenStatsSeeded_ListAndDetailServeThem() throws Exception {
        // No comment rows exist: any non-zero commentCount can only come from planner_stats.
        // The list is asserted BEFORE the detail: the detail request buffers a view
        // whose scheduled flush would otherwise race the list's viewCount read.
        seedStats(42, 5, 7);

        mockMvc.perform(get("/api/planner/md/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].viewCount").value(42))
                .andExpect(jsonPath("$.content[0].upvotes").value(5))
                .andExpect(jsonPath("$.content[0].commentCount").value(7));

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(42))
                .andExpect(jsonPath("$.upvotes").value(5))
                .andExpect(jsonPath("$.commentCount").value(7));
    }

    @Test
    @DisplayName("detail-view-count-async: detail serves the pre-request count; the buffered view lands in planner_stats on flush")
    void detailViewCountAsync_WhenViewed_PreRequestCountServedThenFlushIncrements() throws Exception {
        seedStats(10, 0, 0);

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(10));

        viewRecorder.flush();

        int viewCount = statsRepository.findById(planner.getId())
                .map(PlannerStats::getViewCount)
                .orElse(-1);
        assertThat(viewCount)
                .as("the buffered view increments planner_stats once flushed")
                .isEqualTo(11);
    }
}
