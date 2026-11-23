package org.danteplanner.fixture.wallclock;

import jakarta.persistence.EntityListeners;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@EntityListeners(AuditingEntityListener.class)
public class SpringAuditedFixture {
}
