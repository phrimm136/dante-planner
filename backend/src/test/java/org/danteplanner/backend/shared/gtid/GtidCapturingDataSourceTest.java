package org.danteplanner.backend.shared.gtid;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unwrap delegation: Boot's pool-metrics binder reaches a Hikari pool by unwrapping the datasource
 * bean, so a wrapper that dead-ends {@code unwrap} costs every {@code hikaricp_*} meter.
 */
class GtidCapturingDataSourceTest {

    private final GtidWriteCapture capture = new GtidWriteCapture(new SimpleMeterRegistry());

    /** Answers only for {@link HikariDataSource}, the interface Boot's pool-metrics binder asks for. */
    private static final class StubDataSource implements DataSource {

        private final HikariDataSource pool = new HikariDataSource();

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(pool)) {
                return iface.cast(pool);
            }
            throw new SQLException("not a wrapper for " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(pool);
        }

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }

    @Test
    void unwrap_WhenDelegateCarriesTheInterface_ReturnsTheDelegatesPool() throws SQLException {
        StubDataSource delegate = new StubDataSource();
        GtidCapturingDataSource dataSource = new GtidCapturingDataSource(delegate, capture);

        assertThat(dataSource.unwrap(HikariDataSource.class))
                .isSameAs(delegate.unwrap(HikariDataSource.class));
    }

    @Test
    void unwrap_WhenInterfaceMatchesTheWrapper_ReturnsItself() throws SQLException {
        GtidCapturingDataSource dataSource = new GtidCapturingDataSource(new StubDataSource(), capture);

        assertThat(dataSource.unwrap(GtidCapturingDataSource.class)).isSameAs(dataSource);
    }

    @Test
    void unwrap_WhenNeitherSideCarriesTheInterface_Throws() {
        GtidCapturingDataSource dataSource = new GtidCapturingDataSource(new StubDataSource(), capture);

        assertThatThrownBy(() -> dataSource.unwrap(Runnable.class))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void isWrapperFor_WhenDelegateCarriesTheInterface_ReturnsTrue() throws SQLException {
        GtidCapturingDataSource dataSource = new GtidCapturingDataSource(new StubDataSource(), capture);

        assertThat(dataSource.isWrapperFor(HikariDataSource.class)).isTrue();
        assertThat(dataSource.isWrapperFor(GtidCapturingDataSource.class)).isTrue();
        assertThat(dataSource.isWrapperFor(Runnable.class)).isFalse();
    }
}
