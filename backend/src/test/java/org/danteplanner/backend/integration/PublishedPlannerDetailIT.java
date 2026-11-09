package org.danteplanner.backend.integration;

import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Detail read seam: serving a published planner does not take a row lock, so a second reader
 * is not serialized behind a held write lock.
 */
class PublishedPlannerDetailIT extends ExclusiveDatabaseSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "detail-owner@example.com");
        plannerId = TestDataFactory.createTestPlanner(plannerRepository, owner, true).getId();
    }

    private int detailViewCount() throws Exception {
        String body = mockMvc.perform(get("/api/planner/md/published/{id}", plannerId))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("viewCount").asInt();
    }

    @Test
    void detailRead_WhenConcurrent_DoesNotSerialize() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> transactionTemplate.execute(status -> {
                if (entityManager.find(Planner.class, plannerId, LockModeType.PESSIMISTIC_WRITE) == null) {
                    throw new IllegalStateException("planner not found for lock");
                }
                locked.countDown();
                Hang.await(release, "the test releasing the row lock");
                return null;
            }));

            Hang.await(locked, "the holder taking the row lock");

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
            release.countDown();
            Hang.get(holder, "the lock holder's transaction");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
