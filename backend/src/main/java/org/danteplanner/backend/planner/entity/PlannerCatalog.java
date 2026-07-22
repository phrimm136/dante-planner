package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.danteplanner.backend.planner.converter.KeywordSetConverter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Visible-only browse projection: a row exists exactly while the planner is
 * published, not deleted, and not taken down. Deliberately dumb — scalar copies
 * maintained by the write side, read by the public list/search paths only.
 */
@Entity
@Table(name = "planner_catalog")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerCatalog {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "planner_type", nullable = false)
    private PlannerType plannerType;

    @Column(nullable = false, length = 50)
    @Setter
    private String category;

    @Column(nullable = false)
    @Setter
    private String title;

    @Column(name = "selected_keywords", columnDefinition = "JSON")
    @Setter
    @Convert(converter = KeywordSetConverter.class)
    private Set<String> selectedKeywords;

    @Column(name = "first_published_at", nullable = false)
    private Instant firstPublishedAt;

    @Column(nullable = false)
    @Setter
    @Builder.Default
    private Boolean recommended = false;
}
