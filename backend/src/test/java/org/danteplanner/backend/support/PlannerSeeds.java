package org.danteplanner.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;

public record PlannerSeeds(PlannerRepository planners, PlannerPublicationRepository publications,
        TransactionTemplate transactions) {

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        PlannerSeeds plannerSeeds(PlannerRepository planners, PlannerPublicationRepository publications,
                TransactionTemplate transactions) {
            return new PlannerSeeds(planners, publications, transactions);
        }
    }
}
