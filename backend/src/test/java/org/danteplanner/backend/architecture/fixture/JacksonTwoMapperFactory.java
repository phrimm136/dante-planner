package org.danteplanner.backend.architecture.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JacksonTwoMapperFactory {

    public ObjectMapper mapper() {
        return new ObjectMapper();
    }
}
