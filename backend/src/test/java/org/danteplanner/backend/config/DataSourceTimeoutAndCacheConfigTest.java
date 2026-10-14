package org.danteplanner.backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.zaxxer.hikari.HikariConfig;

import org.danteplanner.backend.shared.config.HikariTuningProperties;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourcesConfig;
import org.danteplanner.backend.shared.config.RoutingDataSourceConfig;
import org.danteplanner.backend.shared.config.StatementCache;
import org.danteplanner.backend.shared.config.TimeoutHierarchy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config assertion for the timeout ledger and the statement cache: the pools this codebase builds
 * by hand, and the properties Spring Boot binds onto the pool it builds itself, must both carry the
 * ratified values rather than a copy of them. Exercises only the connection-free builder methods
 * and the properties files on the classpath, so no database is touched.
 */
class DataSourceTimeoutAndCacheConfigTest {

    private static final String HIKARI_PREFIX = "spring.datasource.hikari";
    private static final String HIKARI_KEY_PREFIX = HIKARI_PREFIX + ".";
    private static final String DRIVER_PROPERTY_PREFIX = HIKARI_KEY_PREFIX + "data-source-properties.";

    private static final String REPLICA = "seoul-replica";

    private static final List<String> MYSQL_BOUND_PROFILES =
            List.of("/application.properties", "/application-dev.properties", "/application-prod.properties");

    private static DataSourceProperties primaryProperties() {
        DataSourceProperties props = new DataSourceProperties();
        props.setUrl("jdbc:mysql://primary:3306/planner");
        props.setUsername("primaryUser");
        props.setPassword("primaryPw");
        return props;
    }

    private static ReadSourceProperties readSourceProperties() {
        ReadSourceProperties.Source replica = new ReadSourceProperties.Source();
        replica.setName(REPLICA);
        replica.setUrl("jdbc:mysql://seoul-replica:3306/planner");
        ReadSourceProperties.Source primary = new ReadSourceProperties.Source();
        primary.setName(ReadSourceProperties.PRIMARY);
        ReadSourceProperties props = new ReadSourceProperties();
        props.setReadSources(List.of(replica, primary));
        return props;
    }

    private static Properties load(String resource) {
        Properties properties = new Properties();
        try (InputStream stream = DataSourceTimeoutAndCacheConfigTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("%s is on the test classpath", resource).isNotNull();
            properties.load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    @Nested
    class RoutingPools {

        private final RoutingDataSourceConfig config =
                new RoutingDataSourceConfig(primaryProperties(), readSourceProperties());

        private final ReadSourcesConfig readSourcesConfig = new ReadSourcesConfig(
                primaryProperties(), new HikariTuningProperties(), readSourceProperties());

        private List<HikariConfig> allPools() {
            return List.of(
                    config.buildPrimaryHikariConfig(),
                    config.buildBulkheadHikariConfig(),
                    readSourcesConfig.buildSourceHikariConfig(REPLICA));
        }

        @Test
        void buildHikariConfig_WhenBuilt_TakesConnectionTimeoutFromThePoolAcquireConstant() {
            assertThat(config.buildPrimaryHikariConfig().getConnectionTimeout())
                    .isEqualTo(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS);
            assertThat(readSourcesConfig.buildSourceHikariConfig(REPLICA).getConnectionTimeout())
                    .isEqualTo(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS);
        }

        @Test
        void buildBulkheadHikariConfig_WhenBuilt_TakesConnectionTimeoutFromTheBulkheadConstant() {
            assertThat(config.buildBulkheadHikariConfig().getConnectionTimeout())
                    .isEqualTo(TimeoutHierarchy.BULKHEAD_ACQUIRE_TIMEOUT_MS);
        }

        @Test
        void buildHikariConfig_WhenBuilt_SetsTheLockWaitInitSqlOnEveryPool() {
            assertThat(allPools())
                    .allSatisfy(pool -> assertThat(pool.getConnectionInitSql())
                            .isEqualTo(TimeoutHierarchy.LOCK_WAIT_INIT_SQL));
        }

        @Test
        void buildHikariConfig_WhenBuilt_SetsTheSocketTimeoutOnEveryPool() {
            assertThat(allPools())
                    .allSatisfy(pool -> assertThat(pool.getDataSourceProperties())
                            .containsEntry(
                                    TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY,
                                    String.valueOf(TimeoutHierarchy.JDBC_SOCKET_TIMEOUT_MS)));
        }

        @Test
        void buildHikariConfig_WhenBuilt_SetsTheStatementCachePropertiesOnEveryPool() {
            assertThat(allPools())
                    .allSatisfy(pool -> assertThat(pool.getDataSourceProperties())
                            .containsAllEntriesOf(StatementCache.DRIVER_PROPERTIES));
        }

        // it blinds @Version optimistic locking
        @Test
        void buildHikariConfig_WhenBuilt_NeverSetsRewriteBatchedStatements() {
            assertThat(allPools())
                    .allSatisfy(pool -> assertThat(pool.getDataSourceProperties())
                            .doesNotContainKey(StatementCache.BANNED_DRIVER_PROPERTY));
        }
    }

    @Nested
    class BootBuiltPool {

        @Test
        void dataSourceProperties_WhenConfigured_CarryTheStatementCacheSettings() {
            Properties base = load("/application.properties");

            for (Map.Entry<String, String> setting : StatementCache.DRIVER_PROPERTIES.entrySet()) {
                assertThat(base.getProperty(DRIVER_PROPERTY_PREFIX + setting.getKey()))
                        .as("%s%s", DRIVER_PROPERTY_PREFIX, setting.getKey())
                        .isEqualTo(setting.getValue());
            }
        }

        @Test
        void dataSourceProperties_WhenConfigured_NeverMentionRewriteBatchedStatements() {
            for (String profile : MYSQL_BOUND_PROFILES) {
                Properties properties = load(profile);
                assertThat(properties.stringPropertyNames())
                        .as("keys of %s", profile)
                        .noneMatch(key -> key.contains(StatementCache.BANNED_DRIVER_PROPERTY));
                assertThat(properties.stringPropertyNames())
                        .as("values of %s", profile)
                        .noneMatch(key -> properties.getProperty(key)
                                .contains(StatementCache.BANNED_DRIVER_PROPERTY));
            }
        }

        @Test
        void connectionTimeout_WhenConfigured_MatchesThePoolAcquireConstant() {
            assertThat(load("/application.properties").getProperty(HIKARI_KEY_PREFIX + "connection-timeout"))
                    .isEqualTo(String.valueOf(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS));
        }

        @Test
        void tomcatConnectionTimeout_WhenConfigured_MatchesTheWriteBoundConstant() {
            assertThat(load("/application.properties").getProperty("server.tomcat.connection-timeout"))
                    .isEqualTo(String.valueOf(TimeoutHierarchy.TOMCAT_CONNECTION_TIMEOUT_MS));
        }

        @Test
        void connectionInitSql_WhenConfigured_MatchesTheLockWaitStatement() {
            assertThat(load("/application-dev.properties").getProperty(HIKARI_KEY_PREFIX + "connection-init-sql"))
                    .isEqualTo(TimeoutHierarchy.LOCK_WAIT_INIT_SQL);
            assertThat(load("/application-prod.properties").getProperty(HIKARI_KEY_PREFIX + "connection-init-sql"))
                    .isEqualTo(TimeoutHierarchy.LOCK_WAIT_INIT_SQL);
        }

        @Test
        void jdbcUrl_WhenAssembled_CarriesTheSocketTimeoutConstant() {
            String expected = TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY
                    + "=" + TimeoutHierarchy.JDBC_SOCKET_TIMEOUT_MS;

            assertThat(load("/application-dev.properties").getProperty("spring.datasource.url"))
                    .contains(expected);
            assertThat(load("/application-prod.properties").getProperty("spring.datasource.url"))
                    .contains(expected);
        }

        // a long migration must not abort at the socket timeout
        @Test
        void flywayUrl_WhenAssembled_CarriesNoSocketTimeout() {
            assertThat(load("/application-prod.properties").getProperty("spring.flyway.url"))
                    .doesNotContain(TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY);
            assertThat(load("/application-dev.properties").getProperty("spring.flyway.url"))
                    .doesNotContain(TimeoutHierarchy.SOCKET_TIMEOUT_PROPERTY);
        }
    }

    @Nested
    class BootBinding {

        private HikariConfig bindBaseProfile() {
            MapConfigurationPropertySource source = new MapConfigurationPropertySource();
            load("/application.properties").forEach(source::put);
            HikariConfig bound = new HikariConfig();
            new Binder(source).bind(HIKARI_PREFIX, Bindable.ofInstance(bound));
            return bound;
        }

        @Test
        void hikariProperties_WhenBound_ReachTheStatementCacheAndPoolAcquire() {
            HikariConfig bound = bindBaseProfile();

            assertThat(bound.getDataSourceProperties())
                    .containsAllEntriesOf(StatementCache.DRIVER_PROPERTIES)
                    .doesNotContainKey(StatementCache.BANNED_DRIVER_PROPERTY);
            assertThat(bound.getConnectionTimeout())
                    .isEqualTo(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS);
        }
    }

    @Nested
    class HibernateProperties {

        // so a varying list reuses one plan
        @Test
        void hibernateProperties_WhenConfigured_EnableInClauseParameterPadding() {
            assertThat(load("/application.properties")
                    .getProperty("spring.jpa.properties.hibernate.query.in_clause_parameter_padding"))
                    .isEqualTo("true");
        }
    }
}
