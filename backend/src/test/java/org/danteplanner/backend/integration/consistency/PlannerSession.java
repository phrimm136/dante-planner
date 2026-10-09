package org.danteplanner.backend.integration.consistency;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.support.UpsertRequests;

import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

public final class PlannerSession implements OperationPerformer {

    private static final String PLANNERS = "/api/planner/md";

    private final String name;
    private final CookieJar jar;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final HistoryRecorder recorder;
    private final Map<Slot, UUID> keys;
    private final Map<Slot, Long> seenVersions = new EnumMap<>(Slot.class);
    private int saves;

    public PlannerSession(String name, CookieJar jar, MockMvc mockMvc, ObjectMapper objectMapper,
            HistoryRecorder recorder, Map<Slot, UUID> keys) {
        this.name = name;
        this.jar = jar;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.recorder = recorder;
        this.keys = Map.copyOf(keys);
    }

    @Override
    public void perform(PlannedOperation operation) throws JsonProcessingException {
        switch (operation) {
            case PlannedOperation.Save save -> save(save.slot());
            case PlannedOperation.Read read -> read(read.slot());
            case PlannedOperation.ListAll list -> list();
            case PlannedOperation.Delete delete -> deletePlanner(delete.slot());
        }
    }

    private void save(Slot slot) throws JsonProcessingException {
        UUID id = keys.get(slot);
        saves++;
        String body = objectMapper.writeValueAsString(UpsertRequests.draft()
                .id(id.toString())
                .title(name + "-" + saves)
                .syncVersion(seenVersions.get(slot))
                .build());
        recorder.save(name, id.toString(), () -> unsafe(put(PLANNERS + "/" + id)
                        .contentType(APPLICATION_JSON)
                        .content(body)))
                .filter(response -> HttpStatusCode.valueOf(response.status()).is2xxSuccessful())
                .ifPresent(response -> see(slot, response.syncVersion()));
    }

    private void read(Slot slot) {
        UUID id = keys.get(slot);
        recorder.read(name, id.toString(), () -> safe(get(PLANNERS + "/" + id)))
                .filter(response -> response.status() == HttpStatus.OK.value())
                .ifPresent(response -> see(slot, response.syncVersion()));
    }

    private void list() {
        recorder.list(name, () -> safe(get(PLANNERS)));
    }

    private void deletePlanner(Slot slot) {
        UUID id = keys.get(slot);
        recorder.delete(name, id.toString(), () -> unsafe(delete(PLANNERS + "/" + id)));
    }

    private void see(Slot slot, long syncVersion) {
        seenVersions.merge(slot, syncVersion, Math::max);
    }

    private Response safe(MockHttpServletRequestBuilder request) throws Exception {
        return exchange(request.cookie(jar.cookies()));
    }

    private Response unsafe(MockHttpServletRequestBuilder request) throws Exception {
        return exchange(request.cookie(jar.cookiesExcept(CookieConstants.CSRF)).with(withCsrf()));
    }

    private Response exchange(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        jar.apply(response.getHeaders(HttpHeaders.SET_COOKIE));
        return new Response(response.getStatus(), response.getContentAsString());
    }
}
