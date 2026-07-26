package org.danteplanner.backend.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * One {@code mysqld} per Gradle fork, one database, one Spring context for every subclass.
 *
 * <p>The property source is declared here rather than per subclass, which is what collapses them
 * onto a single cached context: Spring keys the cache on the set of dynamic-property methods, so a
 * shared method means a shared context, a shared database, and one schema build for the fork
 * instead of one per class.</p>
 *
 * <p>That only holds while subclasses isolate themselves by the rows they write. A test that
 * truncates a table or counts every row observes its neighbours; see
 * {@code backend/src/test/CLAUDE.md} for what a test may assume about data it did not create.</p>
 *
 * <p>The data directory is a tmpfs. DDL is metadata-heavy and the schema build is the tier's
 * dominant cost, so the win is large and the loss is nothing: the container is discarded when the
 * fork ends, making durability worthless.</p>
 */
public abstract class SharedMySqlContainerSupport {

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("it")
            .withUsername("test")
            .withPassword("test")
            .withTmpFs(Map.of("/var/lib/mysql", "rw,size=512m"))
            .withCommand(
                    // The data directory is a tmpfs, so a large buffer pool caches RAM in
                    // RAM; a test database is a schema and a few hundred rows.
                    "--innodb-buffer-pool-size=64M",
                    "--innodb-flush-log-at-trx-commit=0",
                    "--sync-binlog=0",
                    "--innodb-doublewrite=0",
                    "--skip-log-bin",
                    "--performance-schema=OFF",
                    "--skip-name-resolve",
                    // Every cached context keeps its pool open, and concurrent classes raise how
                    // many are live at once.
                    "--max-connections=1000");

    static {
        MYSQL.start();
        grantGlobalPrivilegesToTestUser();
    }

    /**
     * The {@code test} user needs CREATE globally, or the {@code createDatabaseIfNotExist} flag a
     * per-class database is materialized with cannot run.
     */
    private static void grantGlobalPrivilegesToTestUser() {
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("GRANT ALL PRIVILEGES ON *.* TO 'test'@'%'");
            statement.execute("FLUSH PRIVILEGES");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to grant privileges to the shared MySQL test user", e);
        }
    }

    @DynamicPropertySource
    static void sharedDatasource(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry);
    }

    /**
     * Binds a subclass to a database of its own.
     *
     * <p>For a test whose subject is a whole table or index rather than a row it created: an
     * assertion over every published planner, or over what a search term returns, has nothing to
     * narrow. Such a test truncates, and truncating the shared database deletes rows its concurrent
     * neighbours are still using; a lock does not help, since it excludes only other lock holders
     * while the rest of the suite carries on.</p>
     *
     * <p>Costs one Spring context per caller, the database name being part of the context cache
     * key, plus a {@code redis-server} of its own. Prefer narrowing the assertion to rows the test
     * created.</p>
     *
     * @param registry the Spring dynamic property registry
     * @param database a name unique to the calling class
     * @return the JDBC URL bound to that database
     */
    public static String registerOwnDatabase(DynamicPropertyRegistry registry, String database) {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getFirstMappedPort()
                + "/" + database + "?createDatabaseIfNotExist=true";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        SharedRedisContainerSupport.registerOwnRedis(registry, database);
        return url;
    }

    /**
     * For the few subclasses that need extra properties and therefore their own context. They still
     * share this database; a distinct context is not distinct data.
     *
     * @param registry the Spring dynamic property registry
     * @return the shared JDBC URL, for subclasses that also bind a replica to it
     */
    protected static String registerSharedMysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        SharedRedisContainerSupport.registerSharedRedis(registry);
        return MYSQL.getJdbcUrl();
    }
}
