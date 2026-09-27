package org.danteplanner.backend.readpath;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentTombstoneStoreTest {

    @Mock StringRedisTemplate sharedTemplate;
    @Mock StringRedisTemplate authLocalTemplate;
    @Mock ValueOperations<String, String> authLocalValues;

    private SimpleMeterRegistry meterRegistry;
    private ContentTombstoneStore store;

    private final UUID live = UUID.randomUUID();
    private final UUID deleted = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        store = new ContentTombstoneStore(sharedTemplate, authLocalTemplate, meterRegistry);
    }

    @Test
    void tombstonedAmong_WhenSomeKeysExist_ReturnsThoseIdsFromOneMultiGet() {
        when(authLocalTemplate.opsForValue()).thenReturn(authLocalValues);
        when(authLocalValues.multiGet(List.of("del:planner:" + live, "del:planner:" + deleted)))
                .thenReturn(Arrays.asList(null, "1"));

        Set<UUID> tombstoned = store.tombstonedAmong("planner", List.of(live, deleted));

        assertThat(tombstoned).containsExactly(deleted);
        verify(authLocalValues).multiGet(anyList());
        verifyNoInteractions(sharedTemplate);
    }

    @Test
    void tombstonedAmong_WhenRedisFails_FailsOpenAndCountsTheSkip() {
        when(authLocalTemplate.opsForValue()).thenReturn(authLocalValues);
        when(authLocalValues.multiGet(anyList())).thenThrow(new QueryTimeoutException("down"));

        Set<UUID> tombstoned = store.tombstonedAmong("planner", List.of(live, deleted));

        assertThat(tombstoned).isEmpty();
        assertThat(meterRegistry.counter("tombstone.check_skipped").count()).isEqualTo(1d);
    }

    @Test
    void tombstonedAmong_WhenNoIds_SkipsRedis() {
        assertThat(store.tombstonedAmong("planner", List.of())).isEmpty();
        verify(authLocalTemplate, never()).opsForValue();
    }

    @Test
    void clearTombstone_WhenCalled_DeletesTheScopedKeyOnTheSharedStore() {
        store.clearTombstone("published-planner", live);

        verify(sharedTemplate).delete("del:published-planner:" + live);
        verifyNoInteractions(authLocalTemplate);
    }

    @Test
    void clearTombstone_WhenRedisFails_DoesNotThrow() {
        when(sharedTemplate.delete("del:published-planner:" + live)).thenThrow(new QueryTimeoutException("down"));

        store.clearTombstone("published-planner", live);

        verify(sharedTemplate).delete("del:published-planner:" + live);
    }

    private List<ILoggingEvent> warningsDuring(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(ContentTombstoneStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(action::run).doesNotThrowAnyException();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    @Test
    void writeTombstones_WhenRedisFails_LogsAndDoesNotThrow() {
        when(sharedTemplate.executePipelined(any(SessionCallback.class)))
                .thenThrow(new QueryTimeoutException("down"));

        List<ILoggingEvent> warnings = warningsDuring(
                () -> store.writeTombstones("published-planner", List.of(live, deleted)));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getThrowableProxy().getClassName())
                .isEqualTo(QueryTimeoutException.class.getName());
    }

    @Test
    void clearTombstones_WhenRedisFails_LogsAndDoesNotThrow() {
        when(sharedTemplate.delete(anyCollection())).thenThrow(new QueryTimeoutException("down"));

        List<ILoggingEvent> warnings = warningsDuring(
                () -> store.clearTombstones("published-planner", List.of(live, deleted)));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getThrowableProxy().getClassName())
                .isEqualTo(QueryTimeoutException.class.getName());
        verify(sharedTemplate).delete(List.of("del:published-planner:" + live, "del:published-planner:" + deleted));
    }
}
