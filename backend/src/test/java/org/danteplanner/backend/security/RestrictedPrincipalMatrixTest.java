package org.danteplanner.backend.security;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ban withdraws distribution, never possession.
 *
 * <p>Blocked: publishing a planner, and every way of putting text or a score on someone else's
 * content. Everything else stays reachable, including private planner CRUD and import.</p>
 *
 * <p>The endpoint axis comes from {@link RequestMappingHandlerMapping}, so both arms grow on their
 * own. A newly added endpoint lands in the must-not-be-blocked arm, which is the direction where a
 * wrong answer is visible rather than silently permissive.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestrictedPrincipalMatrixTest {

    /**
     * Out of scope for ban semantics. {@code /api/auth} is {@code permitAll} by design, so a banned
     * user must still reach logout and their own status. The moderation and admin trees are
     * role-gated, so a NORMAL principal is rejected by the matcher before any handler runs, and
     * exercising them here only consumes the shared rate-limit buckets other tests depend on.
     */
    private static final Set<String> EXEMPT_PREFIXES =
            Set.of("/api/auth", "/api/moderation", "/api/admin");

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Every guarded path checks the principal before it looks anything up, so a synthetic path
     * variable still reaches the guard. The bodies exist only to clear bean validation, which runs
     * earlier than the controller and would otherwise mask the guard behind a 400.
     */
    private static final Map<String, String> BLOCKED_FOR_BANNED = Map.of(
            "PUT /api/planner/md/{id}/publish", "{\"published\":true}",
            "POST /api/planner/md/{id}/upvote", "{\"voteType\":\"UP\"}",
            "POST /api/planner/md/{id}/report", "{}",
            "POST /api/planner/{plannerId}/comments", "{\"content\":\"body\"}",
            "POST /api/comments/{parentCommentId}/replies", "{\"content\":\"body\"}",
            "PUT /api/comments/{commentId}", "{\"content\":\"body\"}",
            "POST /api/comments/{commentId}/upvote", "{}");

    private static final String BANNED_CODE = "USER_BANNED";

    @Autowired private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired private UserRepository userRepository;
    @Autowired private PlannerRepository plannerRepository;
    @Autowired private JwtTokenService jwtTokenService;

    private Cookie bannedUserCookie;
    private Long bannedUserId;

    @BeforeEach
    void setUp() {
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);

        User banned = TestDataFactory.createTestUser(userRepository, "banned@example.com");
        banned.setBannedAt(Instant.now());
        userRepository.save(banned);

        bannedUserId = banned.getId();
        bannedUserCookie = new Cookie("accessToken", TestDataFactory.generateAccessToken(jwtTokenService, banned));
    }

    /**
     * Dynamic tests share one {@code @BeforeEach}, so an endpoint that lifts the principal's
     * restriction would hand every later case a false pass.
     */
    private void restoreBannedPrincipal() {
        userRepository.findById(bannedUserId).ifPresent(user -> {
            user.setBannedAt(Instant.now());
            userRepository.save(user);
        });
    }

    @TestFactory
    @DisplayName("a ban blocks publishing and commenting, and nothing else")
    Stream<DynamicTest> ban_withdraws_distribution_only() {
        return mutatingApiEndpoints().map(endpoint -> {
            String key = endpoint.method() + " " + endpoint.pattern();
            return DynamicTest.dynamicTest(key, () -> {
                restoreBannedPrincipal();
                MvcResult result = mockMvc.perform(request(endpoint, BLOCKED_FOR_BANNED.get(key))).andReturn();
                String body = result.getResponse().getContentAsString();

                if (BLOCKED_FOR_BANNED.containsKey(key)) {
                    // Controllers rate-limit before delegating, so a throttled request never reaches the
                    // guard. That ordering is intended, and 429 is still a rejection.
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 429 || (status == 403 && body.contains(BANNED_CODE)),
                            key + " must reject a banned user, got " + status + " " + body);
                } else {
                    assertFalse(body.contains(BANNED_CODE),
                            key + " withdrew a capability a ban does not cover: " + body);
                }
            });
        });
    }

    private MockHttpServletRequestBuilder request(Endpoint endpoint, String body) {
        String path = endpoint.pattern().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        return MockMvcRequestBuilders.request(
                        org.springframework.http.HttpMethod.valueOf(endpoint.method()), path)
                .with(withCsrf())
                .cookie(bannedUserCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : body);
    }

    private Stream<Endpoint> mutatingApiEndpoints() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(RestrictedPrincipalMatrixTest::expand)
                .filter(e -> e.pattern().startsWith("/api/"))
                .filter(e -> EXEMPT_PREFIXES.stream().noneMatch(e.pattern()::startsWith))
                .filter(e -> MUTATING_METHODS.contains(e.method()))
                .distinct()
                .sorted(Comparator.comparing(Endpoint::pattern).thenComparing(Endpoint::method));
    }

    private static Stream<Endpoint> expand(RequestMappingInfo info) {
        List<String> patterns = info.getPathPatternsCondition() == null
                ? List.of()
                : info.getPathPatternsCondition().getPatternValues().stream().toList();
        List<String> methods = info.getMethodsCondition().getMethods().stream()
                .map(Enum::name)
                .toList();
        return patterns.stream().flatMap(p -> methods.stream().map(m -> new Endpoint(p, m)));
    }

    private record Endpoint(String pattern, String method) {
    }
}
