package org.danteplanner.backend.support;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;

import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

public final class ApiRequests {

    private static final String UPSERT_PATH = "/api/planner/md/{id}";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiRequests(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public ResultActions upsert(UpsertPlannerRequest request, Cookie... cookies) throws Exception {
        return mockMvc.perform(withCookies(put(UPSERT_PATH, request.id()), cookies)
                .with(withCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    public ResultActions get(String path, Cookie... cookies) throws Exception {
        return mockMvc.perform(withCookies(MockMvcRequestBuilders.get(path), cookies));
    }

    private static MockHttpServletRequestBuilder withCookies(MockHttpServletRequestBuilder request, Cookie... cookies) {
        return cookies.length == 0 ? request : request.cookie(cookies);
    }
}
