package org.danteplanner.backend.integration;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.SpringBootMockMvcBuilderCustomizer;

import io.micrometer.core.instrument.MeterRegistry;

import org.danteplanner.backend.BackendApplication;
import org.danteplanner.backend.config.TestConfig;

final class OregonContext implements AutoCloseable {

    static final long CONNECTION_TIMEOUT_BUDGET_MS = 250L;

    static final int BREAKER_WINDOW = 2;

    static final String REPLICA_SOURCE = "seoul-replica";

    private static final String PROFILE = "it";

    private static final int MARKER_WAIT_REPLICAS = 1;

    private final ConfigurableApplicationContext context;

    private final MockMvc mockMvc;

    private OregonContext(ConfigurableApplicationContext context) {
        this.context = context;
        this.mockMvc = buildMockMvc((WebApplicationContext) context);
    }

    static OregonContext start() {
        return new OregonContext(new SpringApplicationBuilder(BackendApplication.class, TestConfig.class)
                .web(WebApplicationType.SERVLET)
                .profiles(PROFILE)
                .run(arguments()));
    }

    MockMvc mockMvc() {
        return mockMvc;
    }

    MeterRegistry meterRegistry() {
        return context.getBean(MeterRegistry.class);
    }

    @Override
    public void close() {
        context.close();
    }

    private static MockMvc buildMockMvc(WebApplicationContext context) {
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(context);
        SpringBootMockMvcBuilderCustomizer customizer = new SpringBootMockMvcBuilderCustomizer(context);
        customizer.setPrint(MockMvcPrint.NONE);
        customizer.customize(builder);
        return builder.build();
    }

    private static String[] arguments() {
        String primaryUrl = primaryUrlThroughOregonProxy();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", 0);
        properties.put("spring.datasource.url", CausalHarnessSupport.withSessionStateTracking(primaryUrl));
        properties.put("spring.datasource.username", CausalHarnessSupport.PRIMARY.getUsername());
        properties.put("spring.datasource.password", CausalHarnessSupport.PRIMARY.getPassword());
        properties.put("spring.datasource.hikari.connection-timeout", CONNECTION_TIMEOUT_BUDGET_MS);
        properties.put("spring.flyway.enabled", false);
        properties.put("spring.flyway.url", primaryUrl);
        properties.put("spring.flyway.user", CausalHarnessSupport.PRIMARY.getUsername());
        properties.put("spring.flyway.password", CausalHarnessSupport.PRIMARY.getPassword());
        properties.put("redis.auth.host", CausalHarnessSupport.AUTH_REDIS.getRedisHost());
        properties.put("redis.auth.port", CausalHarnessSupport.AUTH_REDIS.getRedisPort());
        properties.put("redis.auth-local.host", CausalHarnessSupport.AUTH_REDIS.getRedisHost());
        properties.put("redis.auth-local.port", CausalHarnessSupport.AUTH_REDIS.getRedisPort());
        properties.put("redis.rate-limit.host", CausalHarnessSupport.RATE_LIMIT_REDIS.getRedisHost());
        properties.put("redis.rate-limit.port", CausalHarnessSupport.RATE_LIMIT_REDIS.getRedisPort());
        properties.put("freshness.marker.wait-replicas", MARKER_WAIT_REPLICAS);
        properties.put("datasource.routing.enabled", true);
        properties.put("datasource.read-sources[0].name", "primary");
        properties.put("datasource.read-sources[1].name", REPLICA_SOURCE);
        properties.put("datasource.read-sources[1].url", CausalHarnessSupport.REPLICA.getJdbcUrl());
        properties.put("datasource.read-sources[1].username", CausalHarnessSupport.REPLICA.getUsername());
        properties.put("datasource.read-sources[1].password", CausalHarnessSupport.REPLICA.getPassword());
        properties.put("datasource.read-source-breaker.sliding-window-size", BREAKER_WINDOW);
        properties.put("datasource.read-source-breaker.minimum-number-of-calls", BREAKER_WINDOW);
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static String primaryUrlThroughOregonProxy() {
        return "jdbc:mysql://" + CausalHarnessSupport.TOXIPROXY.getHost() + ":"
                + CausalHarnessSupport.TOXIPROXY.getMappedPort(CausalHarnessSupport.OREGON_PROXY_LISTEN_PORT) + "/"
                + CausalHarnessSupport.PRIMARY.getDatabaseName();
    }
}
