package org.danteplanner.backend.support;

import java.util.concurrent.atomic.AtomicLong;

import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;

class RecordingRefreshRotationContractTest implements RefreshRotationContract {

    private final AtomicLong userIds = new AtomicLong();
    private final TestJwt jwt = new TestJwt();
    private final RecordingTokenBlacklist blacklist = new RecordingTokenBlacklist(jwt.clock());

    @Override
    public TestJwt jwt() {
        return jwt;
    }

    @Override
    public RefreshRotationService rotation(boolean legacyAdmitEnabled, long retryReuseWindowMs) {
        return new RecordingRefreshRotation(jwt, blacklist, legacyAdmitEnabled, retryReuseWindowMs);
    }

    @Override
    public TokenBlacklistService blacklist() {
        return blacklist;
    }

    @Override
    public Long freshUserId() {
        return userIds.incrementAndGet();
    }
}
