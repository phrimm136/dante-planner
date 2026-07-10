package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-1 scenario test: the Toxiproxy {@code wan} profile (a 130ms latency toxic on the
 * app→primary path) can be applied and removed per test through the harness control API.
 *
 * <p>Determinism (INV4): the profile's effect is asserted through Toxiproxy's own control
 * API — the presence and absence of the toxic on the app→primary proxy — never through a
 * measured wall-clock latency or a sleep. Applying the profile must leave a toxic on the
 * proxy; removing it must leave the proxy with no toxics.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class ToxiproxyWanIT extends CausalHarnessSupport {

    @Test
    @DisplayName("The wan profile can be applied and removed: latency toxic present after apply, absent after remove")
    void wanProfile_applyThenRemove_toxicPresentThenAbsent() throws Exception {
        toxiproxyControl.applyWan();
        assertThat(toxiproxyControl.appToPrimaryProxy().toxics().getAll()).isNotEmpty();

        toxiproxyControl.removeWan();
        assertThat(toxiproxyControl.appToPrimaryProxy().toxics().getAll()).isEmpty();
    }
}
