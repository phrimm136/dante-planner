package org.danteplanner.backend.shared.config;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.security.ChainAuthenticationFilter;
import org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter;
import org.danteplanner.backend.shared.security.CustomAuthenticationEntryPoint;
import org.danteplanner.backend.shared.security.MdcLoggingFilter;
import org.danteplanner.backend.shared.security.ProblemAccessDeniedHandler;
import org.danteplanner.backend.shared.security.SecurityRuleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
public class SecurityConfig {

    private final CsrfDoubleSubmitFilter csrfDoubleSubmitFilter;
    private final ChainAuthenticationFilter authenticationFilter;
    private final MdcLoggingFilter mdcLoggingFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemAccessDeniedHandler accessDeniedHandler;
    private final List<SecurityRuleProvider> ruleContributions;

    /**
     * Spring Boot auto-registers a filter bean in the servlet container, where it runs a second
     * time ahead of the Spring Security chain and outside its ordering.
     */
    @Bean
    public FilterRegistrationBean<CsrfDoubleSubmitFilter> csrfFilterRegistration(
            CsrfDoubleSubmitFilter filter) {
        FilterRegistrationBean<CsrfDoubleSubmitFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ChainAuthenticationFilter> authenticationFilterRegistration(
            ChainAuthenticationFilter filter) {
        FilterRegistrationBean<ChainAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcFilterRegistration(MdcLoggingFilter filter) {
        FilterRegistrationBean<MdcLoggingFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)

            .cors(cors -> {})

            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()

                .requestMatchers(HttpMethod.POST, "/api/auth/logout-all").authenticated()

                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/health/readiness").permitAll()
                .requestMatchers("/actuator/health/liveness").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()

                .requestMatchers("/api/planner/md/published").permitAll()
                .requestMatchers("/api/planner/md/published/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/planner/md/published/{id}/stats").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/planner/md/published/{id}/flags").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/planner/md/published/{id}/viewcount").permitAll()
                .requestMatchers("/api/planner/md/recommended").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/user/associations").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/planner/{plannerId}/comments").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/planner/{plannerId}/comments/events").permitAll();

                ruleContributions.forEach(contribution -> contribution.contribute(auth));

                auth.anyRequest().authenticated();
            })

            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(csrfDoubleSubmitFilter, authenticationFilter.getClass())
            .addFilterAfter(mdcLoggingFilter, authenticationFilter.getClass())

            .headers(headers -> headers
                .xssProtection(xss -> xss.headerValue(
                    XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK
                ))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
            )

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }
}
