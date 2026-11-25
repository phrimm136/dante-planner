package org.danteplanner.backend.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.LockWaits;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class NotificationReadRaceIT extends ExclusiveDatabaseSupport {

    private static final int ROUNDS = 20;
    private static final int UNREAD_ROWS = 10;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private DataSource dataSource;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private JdbcTemplate jdbc;
    private User reader;
    private String token;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        reader = TestDataFactory.createTestUser(userRepository, "read-race@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, reader);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void markRead_WhenDismissedWhileBothWaitForTheRow_KeepsTheFirstReadAtAndBothSucceed() throws Exception {
        Notification raced = raised();
        List<CompletableFuture<MockHttpServletResponse>> answers = new ArrayList<>();

        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM notifications WHERE id = ? FOR UPDATE", raced.getId());
            answers.add(started(() -> answered(markRead(raced.getPublicId()))));
            LockWaits.await(jdbc, 1, answers.get(0)::isDone, "the mark-read waiting for the row");
            answers.add(started(() -> answered(dismiss(raced.getPublicId()))));
            LockWaits.await(jdbc, 2, answers.get(1)::isDone, "the dismissal waiting for the row");
        });
        MockHttpServletResponse marked = Hang.get(answers.get(0), "the mark-read");
        MockHttpServletResponse dismissed = Hang.get(answers.get(1), "the dismissal");

        assertThat(marked.getStatus()).isEqualTo(200);
        assertThat(dismissed.getStatus()).isEqualTo(204);
        assertThat(readAt(raced)).isNotNull().isEqualTo(answeredReadAt(marked));
        assertThat(answeredReadAt(answered(markRead(raced.getPublicId())))).isEqualTo(answeredReadAt(marked));
        assertThat(JsonPath.<Integer>read(answered(get("/api/notifications/inbox")).getContentAsString(),
                "$.totalElements")).isZero();
        assertThat(JsonPath.<Integer>read(answered(get("/api/notifications/unread-count")).getContentAsString(),
                "$.unreadCount")).isZero();
    }

    @Test
    void markRead_WhenEveryNotificationIsDismissedAtOnce_BothSucceedAndLeaveNoneUnread() throws Exception {
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            raised();
            Notification raced = raised();
            raised();

            List<MockHttpServletResponse> answers = atOnce(
                    markRead(raced.getPublicId()), delete("/api/notifications/all"));

            int markedStatus = answers.get(0).getStatus();
            int dismissedStatus = answers.get(1).getStatus();
            if (markedStatus != 200 || dismissedStatus != 200 || unread() != 0) {
                defects.add(round + ": " + markedStatus + " " + dismissedStatus + " unread " + unread());
            }
        }
        assertThat(defects).isEmpty();
    }

    @Test
    void markAllRead_WhenAllAreDismissedAtOnce_BothSucceedAndTheirCountsSumToTheUnreadRows() throws Exception {
        List<String> defects = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            for (int row = 0; row < UNREAD_ROWS; row++) {
                raised();
            }

            List<MockHttpServletResponse> answers = atOnce(
                    post("/api/notifications/mark-all-read"), delete("/api/notifications/all"));

            int affected = 0;
            for (MockHttpServletResponse answer : answers) {
                if (answer.getStatus() != 200) {
                    defects.add(round + ": " + answer.getStatus());
                } else {
                    affected += JsonPath.<Integer>read(answer.getContentAsString(), "$.affected");
                }
            }
            if (affected != UNREAD_ROWS || unread() != 0) {
                defects.add(round + ": affected " + affected + " unread " + unread());
            }
        }
        assertThat(defects).isEmpty();
    }

    private List<MockHttpServletResponse> atOnce(
            MockHttpServletRequestBuilder first, MockHttpServletRequestBuilder second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        List<CompletableFuture<MockHttpServletResponse>> answers = Stream.of(first, second)
                .map(request -> started(() -> {
                    Hang.await(start, "both requests reaching the barrier");
                    return answered(request);
                }))
                .toList();
        return List.of(Hang.get(answers.get(0), "the first request"), Hang.get(answers.get(1), "the second request"));
    }

    private int unread() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class, reader.getId());
    }

    private Notification raised() {
        return notificationRepository.save(new Notification(
                reader.getId(), "read-race-" + UUID.randomUUID(), NotificationType.COMMENT_RECEIVED));
    }

    private Instant readAt(Notification notification) {
        return notificationRepository.findById(notification.getId()).orElseThrow().getReadAt();
    }

    private static Instant answeredReadAt(MockHttpServletResponse response) throws Exception {
        return Instant.parse(JsonPath.read(response.getContentAsString(), "$.readAt"));
    }

    private static MockHttpServletRequestBuilder markRead(UUID publicId) {
        return post("/api/notifications/{id}/mark-read", publicId);
    }

    private static MockHttpServletRequestBuilder dismiss(UUID publicId) {
        return delete("/api/notifications/{id}", publicId);
    }

    private MockHttpServletResponse answered(MockHttpServletRequestBuilder request) {
        try {
            return AuthCookies.performAuthed(mockMvc, request.with(withCsrf()), token).andReturn().getResponse();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private CompletableFuture<MockHttpServletResponse> started(
            Supplier<MockHttpServletResponse> request) {
        return CompletableFuture.supplyAsync(request, executor);
    }
}
