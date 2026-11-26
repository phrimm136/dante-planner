package org.danteplanner.backend.shared.entity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.auditing.AuditingHandler;

import jakarta.persistence.PrePersist;

public class AuditedTimestampListener {

    private final ObjectProvider<AuditingHandler> auditing;

    public AuditedTimestampListener(ObjectProvider<AuditingHandler> auditing) {
        this.auditing = auditing;
    }

    @PrePersist
    public void onCreate(Object entity) {
        auditing.getObject().markCreated(entity);
    }
}
