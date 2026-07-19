package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Upsert conflict seam. A client whose {@code syncVersion} trails the server yields
 * 409 STALE_CLIENT; a true optimistic-lock race (both writers passed the syncVersion check,
 * one loses at flush) yields 409 CONCURRENT_WRITE. Both carry the server version.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerUpsertConflictIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_upsert_conflict_it");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private String token;
    private Planner planner;
    private String validContent;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        owner = TestDataFactory.createTestUser(userRepository, "owner@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        validContent = planner.getContent();
    }

    @AfterEach
    void tearDown() {
        plannerRepository.deleteAll();
    }

    private String body(Long syncVersion) throws Exception {
        return objectMapper.writeValueAsString(new UpsertPlannerRequest(
                planner.getId().toString(), "5F", "Conflict Test", null,
                validContent, 6, PlannerType.MIRROR_DUNGEON, syncVersion, null));
    }

    private MockHttpServletResponse doPut(Long syncVersion) throws Exception {
        return mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                        .cookie(new Cookie("accessToken", token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(body(syncVersion)))
                .andReturn().getResponse();
    }

    @Test
    @DisplayName("upsert_WhenConcurrent_Yields409ConcurrentWrite")
    void upsert_WhenConcurrent_Yields409ConcurrentWrite() throws Exception {
        MockHttpServletResponse conflict = raceUntilConflict();

        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(conflict.getContentAsString()).contains("CONCURRENT_WRITE");
        assertThat(objectMapper.readTree(conflict.getContentAsString()).has("serverVersion")).isTrue();
    }

    /**
     * Fires two upserts that both pass the syncVersion check (each presents the current
     * server syncVersion) and race to flush. Retries the barrier-synchronized race until one
     * request loses — the loser is the conflict response asserted by the caller.
     */
    private MockHttpServletResponse raceUntilConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 60; attempt++) {
                long current = plannerRepository.findById(planner.getId()).orElseThrow().getSyncVersion();
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<MockHttpServletResponse> a = pool.submit(() -> raced(barrier, current));
                Future<MockHttpServletResponse> b = pool.submit(() -> raced(barrier, current));
                MockHttpServletResponse ra = a.get(10, TimeUnit.SECONDS);
                MockHttpServletResponse rb = b.get(10, TimeUnit.SECONDS);
                MockHttpServletResponse loser = pickLoser(ra, rb);
                if (loser != null) {
                    return loser;
                }
            }
            throw new AssertionError("no version-race conflict observed after 60 attempts");
        } finally {
            pool.shutdownNow();
        }
    }

    private MockHttpServletResponse raced(CyclicBarrier barrier, long syncVersion) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return doPut(syncVersion);
    }

    private MockHttpServletResponse pickLoser(MockHttpServletResponse a, MockHttpServletResponse b) {
        if (a.getStatus() >= 400) {
            return a;
        }
        if (b.getStatus() >= 400) {
            return b;
        }
        return null;
    }
}
