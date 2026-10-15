package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.TransactionSpans;
import org.danteplanner.backend.support.TransactionSpans.Span;
import org.danteplanner.backend.support.UpsertRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Upsert conflict seam. Two owner saves of one planner that both present the current
 * {@code syncVersion} serialize on the planner's content row lock: the first commits, and the
 * second reads the winner's committed version and loses at the version check with 409
 * SYNC_CONFLICT, never as a concurrent-write race or a 503.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerUpsertConflictIT extends SharedMySqlContainerSupport {

    private static final int ATTEMPTS = 30;
    private static final int MIN_OVERLAPPING_ATTEMPTS = ATTEMPTS / 2;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;
    private String token;
    private Planner planner;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "owner@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, owner);
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
    }


    private String body(Long syncVersion, String title) throws Exception {
        return objectMapper.writeValueAsString(UpsertRequests.resending(planner)
                .title(title)
                .syncVersion(syncVersion)
                .build());
    }

    private MockHttpServletResponse doPut(Long syncVersion, String title) throws Exception {
        return mockMvc.perform(put("/api/planner/md/{id}", planner.getId())
                        .cookie(AuthCookies.accessToken(token))
                        .with(withCsrf())
                        .contentType(APPLICATION_JSON)
                        .content(body(syncVersion, title)))
                .andReturn().getResponse();
    }

    @Test
    void upsert_WhenTwoSavesPresentTheSameCurrentVersion_OneSucceedsAndTheOtherGetsSyncConflictWithTheWinnersVersion()
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int overlapped = 0;
        try (TransactionSpans.Installed unused = TransactionSpans.install(transactionManager)) {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                long current = plannerRepository.findById(planner.getId()).orElseThrow().getSyncVersion();
                String title = "Conflict Test " + attempt;
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<Timed> a = pool.submit(() -> raced(barrier, current, title));
                Future<Timed> b = pool.submit(() -> raced(barrier, current, title));
                Timed first = a.get(10, TimeUnit.SECONDS);
                Timed second = b.get(10, TimeUnit.SECONDS);

                if (first.span().overlaps(second.span())) {
                    overlapped++;
                }
                List<MockHttpServletResponse> responses = List.of(first.response(), second.response());
                assertThat(responses).extracting(MockHttpServletResponse::getStatus)
                        .as("attempt %d", attempt)
                        .containsExactlyInAnyOrder(200, 409);
                MockHttpServletResponse winner = first.response().getStatus() == 200
                        ? first.response() : second.response();
                MockHttpServletResponse loser = winner == first.response() ? second.response() : first.response();
                JsonNode problem = objectMapper.readTree(loser.getContentAsString());
                assertThat(problem.path("code").asText()).as("attempt %d", attempt).isEqualTo("SYNC_CONFLICT");
                assertThat(problem.path("serverVersion").asLong())
                        .as("attempt %d: the loser is told the winner's new version", attempt)
                        .isEqualTo(objectMapper.readTree(winner.getContentAsString()).path("syncVersion").asLong())
                        .isGreaterThan(current);
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.println("upsert conflict race: " + overlapped + " of " + ATTEMPTS + " attempts overlapped");
        assertThat(overlapped)
                .as("attempts whose two save transactions overlapped, so the second waited on the first's lock")
                .isGreaterThanOrEqualTo(MIN_OVERLAPPING_ATTEMPTS);
    }

    private record Timed(MockHttpServletResponse response, Span span) {
    }

    private Timed raced(CyclicBarrier barrier, long syncVersion, String title) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        Span span = new Span();
        AtomicReference<MockHttpServletResponse> response = new AtomicReference<>();
        TransactionSpans.within(span, () -> {
            try {
                response.set(doPut(syncVersion, title));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return new Timed(response.get(), span);
    }
}
