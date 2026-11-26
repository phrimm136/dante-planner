package org.danteplanner.fixture.wallclock;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;

public class StampedAuditedBase {

    @CreatedDate
    private Instant createdAt;
}
