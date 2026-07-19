package org.danteplanner.backend.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Shared MySQL harness for the single-database integration tests: one {@code mysqld} process
 * per Gradle fork, with every subclass bound to its own database inside that one process.
 *
 * <p>The container is a per-fork singleton — started once in a static block and reused by each
 * subclass in the JVM, so the suite boots one MySQL per fork instead of one per class. Data
 * isolation is preserved by handing each subclass a distinct database via the JDBC
 * {@code createDatabaseIfNotExist} flag; the {@code test} user is granted global privileges at
 * startup so that flag can materialize the database on first connect.</p>
 *
 * <p>Subclasses must declare their OWN {@code @DynamicPropertySource} method (calling
 * {@link #registerSharedMysql}) rather than inheriting one: Spring keys its context cache on the
 * set of dynamic-property methods, so a shared method would collapse subclasses onto a single
 * context — and thus a single database — reintroducing cross-test bleed for the committing tests.</p>
 */
abstract class SharedMySqlContainerSupport {

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--innodb-flush-log-at-trx-commit=0",
                    "--sync-binlog=0",
                    "--performance-schema=OFF",
                    "--skip-name-resolve");

    static {
        MYSQL.start();
        grantGlobalPrivilegesToTestUser();
    }

    /**
     * Registers the datasource and Flyway against a per-subclass database in the shared container.
     * Each subclass passes a name unique to it, so committing tests never observe each other's rows.
     *
     * @param registry the Spring dynamic property registry
     * @param database the subclass-owned database name, created on first connect
     * @return the JDBC URL bound to that database, for subclasses that need it (e.g. a replica datasource)
     */
    protected static String registerSharedMysql(DynamicPropertyRegistry registry, String database) {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getFirstMappedPort()
                + "/" + database + "?createDatabaseIfNotExist=true";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        return url;
    }

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
}
