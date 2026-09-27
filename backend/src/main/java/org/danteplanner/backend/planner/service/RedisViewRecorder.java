package org.danteplanner.backend.planner.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisViewRecorder {

    static final String BUFFER_KEY = "views:buffer";
    static final String SEEN_KEY_PREFIX = "views:seen:";
    static final String BATCH_KEY_PREFIX = "views:batch:";
    static final String BATCHES_KEY = "views:batches";
    static final String RECORD_FAILED_COUNTER = "views.record.failed";

    private static final String KEY_SEPARATOR = ":";
    private static final Duration SEEN_TTL = Duration.ofDays(1);
    private static final Long CLAIMED = 1L;

    private static final String RECORD_SCRIPT = """
            -- KEYS = views:seen:<date>:<plannerId>:<hash>, views:buffer
            -- ARGV = plannerId, seenTtlSeconds
            if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[2]) then
              redis.call('HINCRBY', KEYS[2], ARGV[1], 1)
              return 1
            end
            return 0
            """;

    private static final String CLAIM_SCRIPT = """
            -- KEYS = views:buffer, views:batch:<batchId>, views:batches
            -- ARGV = batchId
            if redis.call('EXISTS', KEYS[1]) == 1 then
              redis.call('RENAME', KEYS[1], KEYS[2])
              redis.call('SADD', KEYS[3], ARGV[1])
              return 1
            end
            return 0
            """;

    private static final String DELETE_SCRIPT = """
            -- KEYS = views:batch:<batchId>, views:batches
            -- ARGV = batchId
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            return 1
            """;

    private final StringRedisTemplate authRedisTemplate;
    private final Counter recordFailed;
    private final DefaultRedisScript<Long> recordScript;
    private final DefaultRedisScript<Long> claimScript;
    private final DefaultRedisScript<Long> deleteScript;

    public RedisViewRecorder(StringRedisTemplate authRedisTemplate, MeterRegistry meterRegistry) {
        this.authRedisTemplate = authRedisTemplate;
        this.recordFailed = Counter.builder(RECORD_FAILED_COUNTER).register(meterRegistry);
        this.recordScript = new DefaultRedisScript<>(RECORD_SCRIPT, Long.class);
        this.claimScript = new DefaultRedisScript<>(CLAIM_SCRIPT, Long.class);
        this.deleteScript = new DefaultRedisScript<>(DELETE_SCRIPT, Long.class);
    }

    public void record(UUID plannerId, String viewerHash, LocalDate viewDate) {
        String seenKey = SEEN_KEY_PREFIX + viewDate + KEY_SEPARATOR + plannerId + KEY_SEPARATOR + viewerHash;
        try {
            authRedisTemplate.execute(recordScript, List.of(seenKey, BUFFER_KEY),
                    plannerId.toString(), String.valueOf(SEEN_TTL.toSeconds()));
        } catch (DataAccessException e) {
            recordFailed.increment();
            log.warn("View for planner {} not recorded: view store unavailable", plannerId, e);
        }
    }

    public boolean claimBuffer(UUID batchId) {
        return CLAIMED.equals(authRedisTemplate.execute(
                claimScript, List.of(BUFFER_KEY, batchKey(batchId), BATCHES_KEY), batchId.toString()));
    }

    public Set<String> leftoverBatchIds() {
        return authRedisTemplate.opsForSet().members(BATCHES_KEY);
    }

    public Map<UUID, Integer> batchIncrements(UUID batchId) {
        return authRedisTemplate.<String, String>opsForHash().entries(batchKey(batchId)).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> UUID.fromString(entry.getKey()),
                        entry -> Integer.parseInt(entry.getValue())));
    }

    public void deleteBatch(UUID batchId) {
        authRedisTemplate.execute(deleteScript, List.of(batchKey(batchId), BATCHES_KEY), batchId.toString());
    }

    private static String batchKey(UUID batchId) {
        return BATCH_KEY_PREFIX + batchId;
    }
}
