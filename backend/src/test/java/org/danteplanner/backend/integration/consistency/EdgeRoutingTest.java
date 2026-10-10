package org.danteplanner.backend.integration.consistency;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SESSIONS;

class EdgeRoutingTest {

    private static final String SESSION_COOKIE = "consistency-session";
    private static final String PLANNERS = "/api/planner/md";
    private static final String VERSION = "{\"syncVersion\":1}";
    private static final String EMPTY_PAGE = "{\"content\":[]}";
    private static final Set<String> WRITES = Set.of("save", "delete", "publish", "unpublish");
    private static final Set<String> READS = Set.of("read", "list", "public-read");

    @Test
    void plan_WhenSeed42RunsAgainstFakeContexts_SendsEveryWriteToOregonAndEveryReadToTheHomeContext()
            throws Exception {
        Plan plan = OperationGenerator.plan(42, 100, 80);
        List<Exchange> exchanges = Collections.synchronizedList(new ArrayList<>());
        List<CookieJar> jars = SESSIONS.stream().map(name -> new CookieJar(new Cookie(SESSION_COOKIE, name))).toList();
        PlannerPool pool = new PlannerPool();
        pool.add(UUID.randomUUID());
        RecordingReplication replication = new RecordingReplication();

        try (HistoryRecorder recorder = new HistoryRecorder(Writer.nullWriter(), System::nanoTime)) {
            PhaseRunner.runPlan(plan, PlannerSession.forPlan(plan, jars, fakeContext(Region.SEOUL, exchanges),
                    fakeContext(Region.OREGON, exchanges), new ObjectMapper(), recorder, pool), replication,
                    replication);
        }

        assertThat(plan.homes()).contains(Region.SEOUL, Region.OREGON);
        assertThat(exchanges).extracting(Exchange::kind).containsAll(WRITES).containsAll(READS);
        assertThat(exchanges).filteredOn(exchange -> WRITES.contains(exchange.kind()))
                .allMatch(exchange -> exchange.region() == Region.OREGON);
        assertThat(exchanges).filteredOn(exchange -> READS.contains(exchange.kind()))
                .allMatch(exchange -> exchange.region() == plan.homes().get(SESSIONS.indexOf(exchange.session())));
    }

    private static MockMvc fakeContext(Region region, List<Exchange> exchanges) {
        return MockMvcBuilders.standaloneSetup(new Object())
                .addFilters((request, response, chain) -> {
                    HttpServletRequest http = (HttpServletRequest) request;
                    HttpServletResponse answer = (HttpServletResponse) response;
                    String kind = kind(http.getMethod(), http.getRequestURI().substring(PLANNERS.length()));
                    exchanges.add(new Exchange(session(http), region, kind));
                    if (kind.equals("delete")) {
                        answer.setStatus(HttpStatus.NO_CONTENT.value());
                        return;
                    }
                    answer.setStatus(HttpStatus.OK.value());
                    answer.getWriter().write(kind.equals("list") ? EMPTY_PAGE : VERSION);
                })
                .build();
    }

    private static String kind(String method, String path) {
        if (method.equals(HttpMethod.PUT.name())) {
            return "save";
        }
        if (method.equals(HttpMethod.DELETE.name())) {
            return "delete";
        }
        if (method.equals(HttpMethod.POST.name())) {
            return path.endsWith("/unpublish") ? "unpublish" : "publish";
        }
        if (path.isEmpty()) {
            return "list";
        }
        return path.startsWith("/published/") ? "public-read" : "read";
    }

    private static String session(HttpServletRequest request) {
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals(SESSION_COOKIE))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow();
    }

    private record Exchange(String session, Region region, String kind) {
    }
}
