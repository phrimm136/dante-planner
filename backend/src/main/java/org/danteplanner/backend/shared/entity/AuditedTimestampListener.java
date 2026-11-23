package org.danteplanner.backend.shared.entity;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.util.Streamable;
import org.springframework.util.Assert;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class AuditedTimestampListener {

    private final ObjectProvider<AuditingHandler> auditing;
    private final ObjectProvider<JpaMetamodelMappingContext> mappings;

    public AuditedTimestampListener(ObjectProvider<AuditingHandler> auditing,
            ObjectProvider<JpaMetamodelMappingContext> mappings) {
        this.auditing = auditing;
        this.mappings = mappings;
    }

    @PrePersist
    public void onCreate(Object entity) {
        AuditingHandler handler = auditing.getObject();
        requireAudited(entity);
        handler.markCreated(entity);
    }

    @PreUpdate
    public void onUpdate(Object entity) {
        AuditingHandler handler = auditing.getObject();
        requireAudited(entity);
        handler.markModified(entity);
    }

    private void requireAudited(Object entity) {
        PersistentEntity<?, ?> mapped = mappings.getObject().getRequiredPersistentEntity(entity.getClass());
        List<Object> stamped = Stream.<Class<? extends Annotation>>of(CreatedDate.class, LastModifiedDate.class)
                .<Object>flatMap(stamp -> Streamable.of(mapped.getPersistentProperties(stamp)).stream())
                .toList();
        Assert.notEmpty(stamped, () -> entity.getClass().getName()
                + " names AuditedTimestampListener without a @CreatedDate or @LastModifiedDate field");
    }
}
