package org.danteplanner.backend.support;

import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

public final class TestJsonMappers {

    private TestJsonMappers() {
    }

    public static JsonMapper springDefaults() {
        return JsonMapper.builderWithJackson2Defaults()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build();
    }
}
