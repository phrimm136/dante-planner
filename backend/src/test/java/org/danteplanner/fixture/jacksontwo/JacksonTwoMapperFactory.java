package org.danteplanner.fixture.jacksontwo;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JacksonTwoMapperFactory {

    public ObjectMapper mapper() {
        return new ObjectMapper();
    }
}
