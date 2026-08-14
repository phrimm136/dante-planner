package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every event type the tree can record has an arm registered to answer for it.
 *
 * <p>A type with no arm is not a missing feature: the dispatcher throws on it, the row stays open,
 * and the relay re-throws on the same row every tick until someone reads the log. The set equality
 * is checked in a real context because registration is what decides it, not the source.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class DomainEffectCoverageIT extends SharedMySqlContainerSupport {

    @Autowired
    private List<DomainEffect> effects;

    @Test
    @DisplayName("the registered arms and the declared event types are the same set")
    void effectArms_WhenRegistered_CoverEveryDeclaredEventType() {
        assertThat(effects)
                .as("two arms declaring one type would leave the registry silently holding one")
                .extracting(DomainEffect::type)
                .containsExactlyInAnyOrder(DomainEventType.values());
    }
}
