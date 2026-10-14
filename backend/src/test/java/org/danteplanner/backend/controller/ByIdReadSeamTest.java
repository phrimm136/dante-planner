package org.danteplanner.backend.controller;

import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.danteplanner.backend.planner.controller.PlannerQueryController;
import org.danteplanner.backend.planner.controller.PublishedPlannerController;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerQueryService;
import org.danteplanner.backend.planner.service.PlannerViewRecorder;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Proves both byId GET endpoints route their service dereference through
 * {@link ByIdReadGuard}: a tombstoned id is masked even though the service still finds it.
 */
@ExtendWith(MockitoExtension.class)
class ByIdReadSeamTest {

    @Mock
    PlannerRepository plannerRepository;

    @Mock
    PlannerStatsRepository plannerStatsRepository;

    private final RecordingTombstoneStore tombstoneStore = new RecordingTombstoneStore();
    private final User owner = TestDataFactory.unsavedUser(42L);
    private final UUID id = UUID.randomUUID();

    PlannerQueryController plannerQueryController;

    PublishedPlannerController publishedPlannerController;

    PublishedPlannerQueryService publishedPlannerQueryService;

    SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        ByIdReadGuard byIdReadGuard = new ByIdReadGuard(
                Optional.of(new PrimaryReCheck(new SimpleMeterRegistry(), tombstoneStore,
                        () -> false, () -> true, RoutingKey.BULKHEAD)));
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(null, plannerRepository);
        plannerQueryController = new PlannerQueryController(
                new PlannerQueryService(plannerRepository, accessGuard), byIdReadGuard);
        securityProperties = new SecurityProperties();
        securityProperties.parseTrustedProxyIps();
        publishedPlannerQueryService = new PublishedPlannerQueryService(
                null, null, null, null, null, new PlannerViewRecorder(null, null), null, plannerStatsRepository,
                accessGuard, null);
        publishedPlannerController = new PublishedPlannerController(
                publishedPlannerQueryService, securityProperties, byIdReadGuard, null);
        tombstoneStore.writeTombstone(ByIdReadGuard.PLANNER_ENTITY_TYPE, id);
    }

    @Test
    void getPlanner_WhenTheServiceFindsATombstonedId_IsMaskedByTheByIdReadGuard() {
        Planner planner = TestDataFactory.planner(owner).id(id).build();
        when(plannerRepository.findAggregateForOwner(id, owner.getId())).thenReturn(Optional.of(planner));

        assertRejected(() -> plannerQueryController.getPlanner(owner.getId(), id)).withCode("NOT_FOUND");
    }

    @Test
    void getPublishedPlanner_WhenTheServiceFindsATombstonedId_IsMaskedByTheByIdReadGuard() {
        Planner planner = TestDataFactory.planner(owner).id(id).published(true).build();
        when(plannerRepository.findPublishedAggregate(id))
                .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(owner))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        request.addHeader("User-Agent", "agent");

        assertRejected(() -> publishedPlannerController.getPublishedPlanner(
                request, new MockHttpServletResponse(), id, null)).withCode("NOT_FOUND");
    }

    @Test
    void getPublishedPlanner_WhenTheGuardHasNoReCheck_ServesTheServiceRead() {
        Planner planner = TestDataFactory.planner(owner).id(id).published(true).build();
        when(plannerRepository.findPublishedAggregate(id))
                .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(owner))));
        PublishedPlannerController withoutReCheck = new PublishedPlannerController(
                publishedPlannerQueryService, securityProperties, new ByIdReadGuard(), null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");

        ResponseEntity<PublishedPlannerDetailResponse> result =
                withoutReCheck.getPublishedPlanner(request, new MockHttpServletResponse(), id, null);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().id()).isEqualTo(id);
    }
}
