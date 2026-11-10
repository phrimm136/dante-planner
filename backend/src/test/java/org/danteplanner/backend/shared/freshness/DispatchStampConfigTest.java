package org.danteplanner.backend.shared.freshness;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchStampConfigTest {

    private static final String ROUTING_ENABLED = "datasource.routing.enabled";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DispatchStampConfig.class);

    @Test
    void dispatchStamp_WhenRoutingIsUnset_IsTheNoOpStamp() {
        runner.run(context -> assertThat(context).getBean(DispatchStamp.class).isInstanceOf(NoDispatchStamp.class));
    }

    @Test
    void dispatchStamp_WhenRoutingIsFalse_IsTheNoOpStamp() {
        runner.withPropertyValues(ROUTING_ENABLED + "=false")
                .run(context -> assertThat(context).getBean(DispatchStamp.class).isInstanceOf(NoDispatchStamp.class));
    }

    @Test
    void dispatchStamp_WhenRoutingIsEmpty_IsTheNoOpStamp() {
        runner.withPropertyValues(ROUTING_ENABLED + "=")
                .run(context -> assertThat(context).getBean(DispatchStamp.class).isInstanceOf(NoDispatchStamp.class));
    }

    @Test
    void dispatchStampConfig_WhenRoutingIsTrue_ContributesNoBean() {
        runner.withPropertyValues(ROUTING_ENABLED + "=true")
                .run(context -> assertThat(context).doesNotHaveBean(DispatchStamp.class));
    }

    @Test
    void dispatchStampConfig_WhenRoutingIsUpperCaseTrue_ContributesNoBean() {
        runner.withPropertyValues(ROUTING_ENABLED + "=TRUE")
                .run(context -> assertThat(context).doesNotHaveBean(DispatchStamp.class));
    }
}
