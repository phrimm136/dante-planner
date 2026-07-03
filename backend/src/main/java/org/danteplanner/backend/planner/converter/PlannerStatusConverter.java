package org.danteplanner.backend.planner.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.danteplanner.backend.planner.entity.PlannerStatus;

/**
 * JPA AttributeConverter mapping {@link PlannerStatus} to the lowercase
 * {@code draft}/{@code saved} values of the MySQL {@code ENUM('draft','saved')} column.
 *
 * <p>Used instead of {@code @Enumerated(STRING)}, which would persist {@code DRAFT}/{@code SAVED}
 * and be rejected by the column.</p>
 */
@Converter
public class PlannerStatusConverter implements AttributeConverter<PlannerStatus, String> {

    @Override
    public String convertToDatabaseColumn(PlannerStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public PlannerStatus convertToEntityAttribute(String dbData) {
        return PlannerStatus.fromValue(dbData);
    }
}
