package org.danteplanner.backend.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.danteplanner.backend.shared.redis.RedisKeyScanner;

public final class TokenStoreReads {

    private TokenStoreReads() {
    }

    public static int blacklistSize(StringRedisTemplate store) {
        return RedisKeyScanner.scanKeys(store, TokenBlacklistService.BLACKLIST_KEY_PREFIX + "*").size();
    }

    public static int userInvalidationSize(StringRedisTemplate store) {
        return RedisKeyScanner.scanKeys(store, TokenBlacklistService.USER_INVALIDATION_KEY_PREFIX + "*").size();
    }

    public static boolean isFamilyRevoked(StringRedisTemplate store, String familyId) {
        return store.opsForHash().hasKey(RefreshRotationService.familyKey(familyId), RefreshRotationService.REVOKED_FIELD);
    }
}
