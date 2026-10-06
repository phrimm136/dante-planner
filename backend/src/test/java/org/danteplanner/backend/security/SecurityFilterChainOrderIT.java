package org.danteplanner.backend.security;

import jakarta.servlet.Filter;
import org.danteplanner.backend.auth.security.JwtAuthenticationFilter;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;
import org.danteplanner.backend.shared.security.MdcLoggingFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class SecurityFilterChainOrderIT extends SharedMySqlContainerSupport {

    @Autowired private FilterChainProxy filterChainProxy;

    @Test
    void securityChain_WhenAssembled_RunsCsrfThenAuthenticationThenMdcBeforeExceptionTranslation() {
        List<Class<? extends Filter>> chain = filterChainProxy.getFilterChains().get(0).getFilters().stream()
                .<Class<? extends Filter>>map(Filter::getClass)
                .toList();

        int csrf = chain.indexOf(CsrfDoubleSubmitFilter.class);
        assertThat(chain.subList(csrf, csrf + 3))
                .containsExactly(CsrfDoubleSubmitFilter.class, JwtAuthenticationFilter.class, MdcLoggingFilter.class);
        assertThat(chain.indexOf(ExceptionTranslationFilter.class)).isGreaterThan(csrf + 2);
    }
}
