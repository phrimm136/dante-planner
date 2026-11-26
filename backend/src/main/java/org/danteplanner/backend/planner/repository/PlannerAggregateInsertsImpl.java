package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;

class PlannerAggregateInsertsImpl implements PlannerAggregateInserts {

    private final PlannerContentRepository contentRepository;
    private final PlannerPublicationRepository publicationRepository;
    private final PlannerModerationRepository moderationRepository;

    PlannerAggregateInsertsImpl(PlannerContentRepository contentRepository,
            PlannerPublicationRepository publicationRepository, PlannerModerationRepository moderationRepository) {
        this.contentRepository = contentRepository;
        this.publicationRepository = publicationRepository;
        this.moderationRepository = moderationRepository;
    }

    @Override
    public void insertSatellites(PlannerContent content) {
        contentRepository.insert(content);
        publicationRepository.insert(PlannerPublication.draft(content.getPlannerId()));
        moderationRepository.insert(PlannerModeration.clear(content.getPlannerId()));
    }
}
