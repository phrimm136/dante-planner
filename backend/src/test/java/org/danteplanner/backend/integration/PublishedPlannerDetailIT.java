package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Detail read seam: serving a published planner does not take a row lock (so a second reader
 * is not serialized behind a held write lock), and the response returns the pre-request view
 * count while the view row is persisted only when the buffer is later flushed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PublishedPlannerDetailIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "published_planner_detail_it");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerViewRepository plannerViewRepository;

    @Autowired
    private PlannerViewRecorder recorder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        plannerViewRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        User owner = TestDataFactory.createTestUser(userRepository, "detail-owner@example.com");
        plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId();
    }

    private int detailViewCount() throws Exception {
        String body = mockMvc.perform(get("/api/planner/md/published/{id}", plannerId))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("viewCount").asInt();
    }

    @Test
    @DisplayName("detailRead_WhenConcurrent_DoesNotSerialize")
    void detailRead_WhenConcurrent_DoesNotSerialize() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> transactionTemplate.execute(status -> {
                if (plannerRepository.findByIdForUpdate(plannerId).isEmpty()) {
                    throw new IllegalStateException("planner not found for lock");
                }
                locked.countDown();
                try {
                    release.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));

            assertThat(locked.await(15, TimeUnit.SECONDS)).isTrue();

            Future<Integer> reader = pool.submit(this::detailViewCount);
            boolean blockedOnRowLock;
            try {
                reader.get(5, TimeUnit.SECONDS);
                blockedOnRowLock = false;
            } catch (TimeoutException e) {
                blockedOnRowLock = true;
                reader.cancel(true);
            }

            assertThat(blockedOnRowLock)
                    .as("a detail read must not serialize behind a held row lock")
                    .isFalse();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("detailRead_WhenServed_ReturnsBeforeViewWrite")
    void detailRead_WhenServed_ReturnsBeforeViewWrite() throws Exception {
        int before = plannerRepository.findById(plannerId).orElseThrow().getViewCount();

        int responseViewCount = detailViewCount();

        assertThat(responseViewCount)
                .as("the detail response returns the pre-request view count, not a synchronous increment")
                .isEqualTo(before);

        recorder.flush();
        assertThat(plannerViewRepository.count())
                .as("the view is persisted by the later flush")
                .isEqualTo(1L);
    }
}
