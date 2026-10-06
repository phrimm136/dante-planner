package org.danteplanner.backend.architecture;

import java.net.URL;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringTestShadowVersionTest {

    private static final String SHADOWED_VERSION = "6.2.19";

    @Test
    void springTestVersion_WhenReadFromJarManifest_MatchesTheShadowedVersion() {
        String version = TestContext.class.getPackage().getImplementationVersion();

        assertThat(version)
                .as("spring-test is now %s: re-derive the shadowed DefaultCacheAwareContextLoaderDelegate from "
                        + "that version's source, or delete it if Spring ships per-configuration loading", version)
                .isEqualTo(SHADOWED_VERSION);
    }

    @Test
    void contextLoaderDelegate_WhenLoaded_ComesFromTheTestClasses() {
        URL loaded = DefaultCacheAwareContextLoaderDelegate.class.getProtectionDomain().getCodeSource().getLocation();
        URL testClasses = SpringTestShadowVersionTest.class.getProtectionDomain().getCodeSource().getLocation();

        assertThat(loaded)
                .as("the shadow is not on the classpath ahead of spring-test")
                .isEqualTo(testClasses);
    }
}
