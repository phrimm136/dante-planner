package org.danteplanner.backend.integration.consistency;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.support.UpsertRequests;

import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.SESSIONS;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

public final class PlannerSession implements OperationPerformer {

    private static final String PLANNERS = "/api/planner/md";
    private static final String PUBLISHED = PLANNERS + "/published";

    private final String name;
    private final CookieJar jar;
    private final MockMvc writes;
    private final MockMvc reads;
    private final ObjectMapper objectMapper;
    private final HistoryRecorder recorder;
    private final PlannerPool pool;
    private final Map<UUID, Long> seenVersions = new HashMap<>();
    private int saves;

    public PlannerSession(String name, CookieJar jar, MockMvc writes, MockMvc reads, ObjectMapper objectMapper,
            HistoryRecorder recorder, PlannerPool pool) {
        this.name = name;
        this.jar = jar;
        this.writes = writes;
        this.reads = reads;
        this.objectMapper = objectMapper;
        this.recorder = recorder;
        this.pool = pool;
    }

    public static List<PlannerSession> forPlan(Plan plan, List<CookieJar> jars, MockMvc seoul, MockMvc oregon,
            ObjectMapper objectMapper, HistoryRecorder recorder, PlannerPool pool) {
        return IntStream.range(0, SESSIONS.size())
                .mapToObj(index -> new PlannerSession(SESSIONS.get(index), jars.get(index), oregon,
                        plan.homes().get(index) == Region.SEOUL ? seoul : oregon, objectMapper, recorder, pool))
                .toList();
    }

    @Override
    public void perform(PlannedOperation operation) throws JacksonException {
        switch (operation) {
            case PlannedOperation.Save save -> save(pool.pick(save.key()));
            case PlannedOperation.Create create -> create();
            case PlannedOperation.Read read -> read(pool.pick(read.key()));
            case PlannedOperation.ListAll list -> list();
            case PlannedOperation.Publish publish -> publish(pool.pick(publish.key()));
            case PlannedOperation.Unpublish unpublish -> unpublish(pool.pick(unpublish.key()));
            case PlannedOperation.PublicRead read -> publicRead(pool.pick(read.key()));
            case PlannedOperation.Delete delete -> deletePlanner(pool.pick(delete.key()));
        }
    }

    private void create() throws JacksonException {
        UUID id = UUID.randomUUID();
        if (save(id).isPresent()) {
            pool.add(id);
        }
    }

    private Optional<Response> save(UUID id) throws JacksonException {
        saves++;
        String body = objectMapper.writeValueAsString(UpsertRequests.draft()
                .id(id.toString())
                .title(name + "-" + saves)
                .syncVersion(seenVersions.get(id))
                .build());
        Optional<Response> applied = recorder.save(name, id.toString(), () -> unsafe(put(PLANNERS + "/" + id)
                        .contentType(APPLICATION_JSON)
                        .content(body)))
                .filter(response -> HttpStatusCode.valueOf(response.status()).is2xxSuccessful());
        applied.ifPresent(response -> see(id, response.syncVersion()));
        return applied;
    }

    private void read(UUID id) {
        recorder.read(name, id.toString(), () -> safe(get(PLANNERS + "/" + id)))
                .filter(PlannerSession::ok)
                .ifPresent(response -> see(id, response.syncVersion()));
    }

    private void list() {
        recorder.list(name, () -> safe(get(PLANNERS)))
                .filter(PlannerSession::ok)
                .ifPresent(response -> response.plannerVersions()
                        .forEach((id, version) -> see(UUID.fromString(id), version)));
    }

    private void publish(UUID id) {
        recorder.publish(name, id.toString(), () -> unsafe(post(PLANNERS + "/" + id + "/publish")))
                .filter(PlannerSession::ok)
                .ifPresent(response -> see(id, response.syncVersion()));
    }

    private void unpublish(UUID id) {
        recorder.unpublish(name, id.toString(), () -> unsafe(post(PLANNERS + "/" + id + "/unpublish")))
                .filter(PlannerSession::ok)
                .ifPresent(response -> see(id, response.syncVersion()));
    }

    private void publicRead(UUID id) {
        recorder.publicRead(name, id.toString(), () -> safe(get(PUBLISHED + "/" + id)))
                .filter(PlannerSession::ok)
                .ifPresent(response -> see(id, response.syncVersion()));
    }

    private void deletePlanner(UUID id) {
        recorder.delete(name, id.toString(), () -> unsafe(delete(PLANNERS + "/" + id)));
    }

    private static boolean ok(Response response) {
        return response.status() == HttpStatus.OK.value();
    }

    private void see(UUID id, long syncVersion) {
        seenVersions.merge(id, syncVersion, Math::max);
    }

    private Response safe(MockHttpServletRequestBuilder request) throws Exception {
        return exchange(reads, request.cookie(jar.cookies()));
    }

    private Response unsafe(MockHttpServletRequestBuilder request) throws Exception {
        return exchange(writes, request.cookie(jar.cookiesExcept(CookieConstants.CSRF)).with(withCsrf()));
    }

    private Response exchange(MockMvc context, MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = context.perform(request).andReturn().getResponse();
        jar.apply(response.getHeaders(HttpHeaders.SET_COOKIE));
        return new Response(response.getStatus(), response.getContentAsString());
    }
}
