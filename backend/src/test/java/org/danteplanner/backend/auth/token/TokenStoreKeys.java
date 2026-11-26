package org.danteplanner.backend.auth.token;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.danteplanner.backend.support.RedisKeyScanner;

public final class TokenStoreKeys {

    private TokenStoreKeys() {
    }

    public static int blacklistSize(StringRedisTemplate store) {
        return RedisKeyScanner.scanKeys(store, TokenBlacklistService.BLACKLIST_KEY_PREFIX + "*").size();
    }

    public static int userInvalidationSize(StringRedisTemplate store) {
        return RedisKeyScanner.scanKeys(store, TokenBlacklistService.USER_INVALIDATION_KEY_PREFIX + "*").size();
    }

    public static void deleteBlacklistAndInvalidations(StringRedisTemplate store) {
        Set<String> keys = new HashSet<>(
                RedisKeyScanner.scanKeys(store, TokenBlacklistService.BLACKLIST_KEY_PREFIX + "*"));
        keys.addAll(RedisKeyScanner.scanKeys(store, TokenBlacklistService.USER_INVALIDATION_KEY_PREFIX + "*"));
        if (!keys.isEmpty()) {
            store.delete(keys);
        }
    }

    public static boolean isFamilyRevoked(StringRedisTemplate store, String familyId) {
        return store.opsForHash().hasKey(RefreshRotationService.familyKey(familyId), RefreshRotationService.REVOKED_FIELD);
    }
}
