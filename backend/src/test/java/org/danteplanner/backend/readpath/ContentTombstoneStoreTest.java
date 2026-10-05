package org.danteplanner.backend.readpath;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisOperations;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentTombstoneStoreTest {

    @Mock StringRedisTemplate sharedTemplate;
    @Mock StringRedisTemplate authLocalTemplate;
    @Mock ValueOperations<String, String> authLocalValues;
    @Mock ValueOperations<String, String> sharedValues;
    @Mock RedisOperations<String, String> pipeline;

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
    void clearTombstoneOrThrow_WhenCalled_DeletesTheScopedKeyOnTheSharedStore() {
        store.clearTombstoneOrThrow("published-planner", live);

        verify(sharedTemplate).delete("del:published-planner:" + live);
        verifyNoInteractions(authLocalTemplate);
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void writeTombstones_WhenCalled_SetsEveryScopedKeyWithABoundedTtlInOnePipeline() {
        ArgumentCaptor<SessionCallback> callback = ArgumentCaptor.forClass(SessionCallback.class);

        store.writeTombstones("published-planner", List.of(live, deleted));

        verify(sharedTemplate).executePipelined(callback.capture());
        when(pipeline.opsForValue()).thenReturn(sharedValues);
        assertThat(callback.getValue().execute(pipeline)).isNull();
        verify(sharedValues).set("del:published-planner:" + live, "1", Duration.ofHours(1));
        verify(sharedValues).set("del:published-planner:" + deleted, "1", Duration.ofHours(1));
        verifyNoInteractions(authLocalTemplate);
    }

    @Test
    void writeTombstones_WhenNoIds_SkipsRedis() {
        store.writeTombstones("published-planner", List.of());

        verifyNoInteractions(sharedTemplate, authLocalTemplate);
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
    void writeTombstone_WhenRedisFails_LogsAndDoesNotThrow() {
        when(sharedTemplate.opsForValue()).thenReturn(sharedValues);
        doThrow(new QueryTimeoutException("down")).when(sharedValues)
                .set("del:planner:" + deleted, "1", Duration.ofHours(1));

        List<ILoggingEvent> warnings = warningsDuring(() -> store.writeTombstone("planner", deleted));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getThrowableProxy().getClassName())
                .isEqualTo(QueryTimeoutException.class.getName());
    }

    @Test
    void writeTombstone_WhenCalled_SetsTheScopedKeyWithABoundedTtlOnTheSharedStore() {
        when(sharedTemplate.opsForValue()).thenReturn(sharedValues);

        store.writeTombstone("planner", deleted);

        verify(sharedValues).set("del:planner:" + deleted, "1", Duration.ofHours(1));
        verifyNoInteractions(authLocalTemplate);
    }

    @Test
    void writeTombstoneOrThrow_WhenCalled_SetsTheScopedKeyWithABoundedTtl() {
        when(sharedTemplate.opsForValue()).thenReturn(sharedValues);

        store.writeTombstoneOrThrow("published-planner", deleted);

        verify(sharedValues).set("del:published-planner:" + deleted, "1", Duration.ofHours(1));
    }

    @Test
    void writeTombstoneOrThrow_WhenRedisFails_Throws() {
        when(sharedTemplate.opsForValue()).thenReturn(sharedValues);
        doThrow(new QueryTimeoutException("down")).when(sharedValues)
                .set("del:published-planner:" + deleted, "1", Duration.ofHours(1));

        assertThatThrownBy(
                        () -> store.writeTombstoneOrThrow("published-planner", deleted))
                .isInstanceOf(QueryTimeoutException.class)
                .hasMessage("down");
    }

    @Test
    void clearTombstoneOrThrow_WhenRedisFails_Throws() {
        when(sharedTemplate.delete("del:published-planner:" + live)).thenThrow(new QueryTimeoutException("down"));

        assertThatThrownBy(
                        () -> store.clearTombstoneOrThrow("published-planner", live))
                .isInstanceOf(QueryTimeoutException.class)
                .hasMessage("down");
    }

    @Test
    void isTombstoned_WhenTheKeyExists_AnswersTrueFromTheLocalStore() {
        when(authLocalTemplate.hasKey("del:planner:" + deleted)).thenReturn(true);
        when(authLocalTemplate.hasKey("del:planner:" + live)).thenReturn(false);

        assertThat(store.isTombstoned("planner", deleted)).isTrue();
        assertThat(store.isTombstoned("planner", live)).isFalse();
        verifyNoInteractions(sharedTemplate);
    }

    @Test
    void isTombstoned_WhenRedisFails_FailsOpenAndCountsTheSkip() {
        when(authLocalTemplate.hasKey("del:planner:" + deleted)).thenThrow(new QueryTimeoutException("down"));

        assertThat(store.isTombstoned("planner", deleted)).isFalse();
        assertThat(meterRegistry.counter("tombstone.check_skipped").count()).isEqualTo(1d);
    }
}
