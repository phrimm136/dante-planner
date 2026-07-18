package org.danteplanner.backend.integration;

import com.redis.testcontainers.RedisContainer;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

/**
 * Base class for the causal-consistency integration tests: boots a shared MySQL
 * primary/replica pair wired with GTID replication, like a Seoul pod.
 *
 * <p>The pair is a process-wide singleton — the static containers start once and stay up
 * for the JVM lifetime, so every subclass context reuses the same replicating databases.
 * The app under test binds its main datasource to the primary via {@link DynamicPropertySource};
 * the harness exposes the primary and replica as qualified {@link JdbcTemplate} beans and hands
 * both to {@link ReplicationControl}.</p>
 *
 * <p>No timing constants live here (INV4): replication readiness is asserted through
 * {@link ReplicationControl#awaitCaughtUp()} on the caller's side, never a sleep.</p>
 */
@Import(CausalHarnessSupport.HarnessDataSourceConfig.class)
abstract class CausalHarnessSupport {

    private static final Logger log = LoggerFactory.getLogger(CausalHarnessSupport.class);

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String PRIMARY_ALIAS = "mysql-primary";
    private static final int MYSQL_INTERNAL_PORT = 3306;
    private static final String ROOT_USER = "root";
    private static final String REPL_USER = "repl";
    private static final String REPL_PASSWORD = "repl-pass";

    private static final String REDIS_IMAGE = "redis:7-alpine";

    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.5.0";
    private static final String APP_TO_PRIMARY_PROXY_NAME = "app-to-primary";
    private static final int PROXY_LISTEN_PORT = 8666;

    private static final Network NETWORK = Network.newNetwork();

    // Relaxed durability and no performance_schema: throwaway test databases need no
    // crash-safety, and GTID replication depends on neither fsync timing nor
    // performance_schema — the flags cut boot time and per-instance memory.
    static final MySQLContainer PRIMARY = new MySQLContainer(MYSQL_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases(PRIMARY_ALIAS)
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--server-id=1",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--gtid-mode=ON",
                    "--enforce-gtid-consistency=ON",
                    "--innodb-flush-log-at-trx-commit=0",
                    "--sync-binlog=0",
                    "--performance-schema=OFF",
                    "--skip-name-resolve");

    static final MySQLContainer REPLICA = new MySQLContainer(MYSQL_IMAGE)
            .withNetwork(NETWORK)
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--server-id=2",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--gtid-mode=ON",
                    "--enforce-gtid-consistency=ON",
                    "--innodb-flush-log-at-trx-commit=0",
                    "--sync-binlog=0",
                    "--performance-schema=OFF",
                    "--skip-name-resolve");

    static final RedisContainer AUTH_REDIS = new RedisContainer(REDIS_IMAGE);

    static final RedisContainer RATE_LIMIT_REDIS = new RedisContainer(REDIS_IMAGE);

    static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer(TOXIPROXY_IMAGE)
            .withNetwork(NETWORK);

    static final Proxy APP_TO_PRIMARY_PROXY;

    static {
        PRIMARY.start();
        REPLICA.start();
        AUTH_REDIS.start();
        RATE_LIMIT_REDIS.start();
        TOXIPROXY.start();
        APP_TO_PRIMARY_PROXY = createAppToPrimaryProxy();
        wireReplication(new JdbcTemplate(adminDataSource(PRIMARY)), new JdbcTemplate(adminDataSource(REPLICA)));
    }

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    protected ReplicationControl replicationControl;

    protected ToxiproxyControl toxiproxyControl;

    @DynamicPropertySource
    static void primaryDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
        registry.add("spring.datasource.username", PRIMARY::getUsername);
        registry.add("spring.datasource.password", PRIMARY::getPassword);
        registry.add("spring.flyway.url", PRIMARY::getJdbcUrl);
        registry.add("spring.flyway.user", PRIMARY::getUsername);
        registry.add("spring.flyway.password", PRIMARY::getPassword);
        registry.add("redis.auth.host", AUTH_REDIS::getRedisHost);
        registry.add("redis.auth.port", AUTH_REDIS::getRedisPort);
        registry.add("redis.auth-local.host", AUTH_REDIS::getRedisHost);
        registry.add("redis.auth-local.port", AUTH_REDIS::getRedisPort);
        registry.add("redis.rate-limit.host", RATE_LIMIT_REDIS::getRedisHost);
        registry.add("redis.rate-limit.port", RATE_LIMIT_REDIS::getRedisPort);
    }

    @BeforeEach
    void bootstrapReplicationControl() throws IOException {
        replicationControl = new ReplicationControl(primaryJdbcTemplate, replicaJdbcTemplate);
        replicaJdbcTemplate.execute("START REPLICA");
        toxiproxyControl = new ToxiproxyControl(APP_TO_PRIMARY_PROXY);
        toxiproxyControl.removeWan();
    }

    /**
     * Creates the app→primary Toxiproxy proxy: an additive path from the toxiproxy container to
     * {@code mysql-primary} that later phases route WAN and partition profiles through. The app's
     * own datasource still binds directly to the primary container ({@link #primaryDatasourceProperties}),
     * so this proxy carries no live traffic — it only holds the toxics the harness inspects.
     */
    private static Proxy createAppToPrimaryProxy() {
        try {
            ToxiproxyClient client = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
            return client.createProxy(
                    APP_TO_PRIMARY_PROXY_NAME,
                    "0.0.0.0:" + PROXY_LISTEN_PORT,
                    PRIMARY_ALIAS + ":" + MYSQL_INTERNAL_PORT);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the app→primary Toxiproxy proxy", e);
        }
    }

    /**
     * Establishes GTID replication from the freshly booted replica to the primary.
     *
     * <p>Purges the primary's pre-replication history on the replica ({@code gtid_purged}) so the
     * two independently bootstrapped databases do not fight over their own init transactions, then
     * points the replica at the primary with {@code SOURCE_AUTO_POSITION=1} — the same incantation
     * as the RDS migration runbook.</p>
     */
    private static void wireReplication(JdbcTemplate primary, JdbcTemplate replica) {
        primary.execute("CREATE USER IF NOT EXISTS '" + REPL_USER + "'@'%' "
                + "IDENTIFIED WITH mysql_native_password BY '" + REPL_PASSWORD + "'");
        primary.execute("GRANT REPLICATION SLAVE ON *.* TO '" + REPL_USER + "'@'%'");
        primary.execute("FLUSH PRIVILEGES");

        String primaryGtid = primary.queryForObject("SELECT @@GLOBAL.gtid_executed", String.class);

        replica.execute("STOP REPLICA");
        replica.execute("RESET REPLICA ALL");
        replica.execute("RESET MASTER");
        if (primaryGtid != null && !primaryGtid.isBlank()) {
            replica.execute("SET GLOBAL gtid_purged = '" + primaryGtid.replaceAll("\\s+", "") + "'");
        }
        replica.execute("CHANGE REPLICATION SOURCE TO "
                + "SOURCE_HOST='" + PRIMARY_ALIAS + "', "
                + "SOURCE_PORT=" + MYSQL_INTERNAL_PORT + ", "
                + "SOURCE_USER='" + REPL_USER + "', "
                + "SOURCE_PASSWORD='" + REPL_PASSWORD + "', "
                + "SOURCE_AUTO_POSITION=1");
        replica.execute("START REPLICA");
        log.info("GTID replication wired: replica {} following primary {}",
                REPLICA.getJdbcUrl(), PRIMARY.getJdbcUrl());
    }

    private static DataSource adminDataSource(MySQLContainer container) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName(container.getDriverClassName());
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(ROOT_USER);
        dataSource.setPassword(container.getPassword());
        dataSource.setSuppressClose(true);
        return dataSource;
    }

    /**
     * Exposes the primary and replica as distinct qualified {@link JdbcTemplate} beans, each backed
     * by its own root-privileged connection to the corresponding container so the harness can drive
     * replication control alongside ordinary reads and writes.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class HarnessDataSourceConfig {

        @Bean
        JdbcTemplate primaryJdbcTemplate() {
            return new JdbcTemplate(adminDataSource(PRIMARY));
        }

        @Bean
        JdbcTemplate replicaJdbcTemplate() {
            return new JdbcTemplate(adminDataSource(REPLICA));
        }
    }
}
