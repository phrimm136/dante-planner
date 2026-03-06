package org.danteplanner.backend.config;

import org.danteplanner.backend.security.CustomAuthenticationEntryPoint;
import org.danteplanner.backend.security.JwtAuthenticationFilter;
import org.danteplanner.backend.security.MdcLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MdcLoggingFilter mdcLoggingFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            MdcLoggingFilter mdcLoggingFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.mdcLoggingFilter = mdcLoggingFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * Prevents Spring Boot from auto-registering MdcLoggingFilter in the servlet container.
     * Without this, the filter runs twice: once at the servlet level (before JwtAuthenticationFilter,
     * so SecurityContext is empty and userId = "guest") and once inside the Spring Security chain.
     * We manage the filter exclusively via addFilterAfter() in securityFilterChain().
     */
    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcFilterRegistration(MdcLoggingFilter filter) {
        FilterRegistrationBean<MdcLoggingFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    /**
     * Defines the role hierarchy: ADMIN > MODERATOR > NORMAL.
     * This means ADMIN automatically has all MODERATOR and NORMAL permissions.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("MODERATOR")
                .role("MODERATOR").implies("NORMAL")
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF: Disabled - SameSite=Lax cookies provide CSRF protection.
            // Lax allows cookies on top-level GET navigation (clicking links) but blocks
            // embedded requests and cross-site POSTs. Safe because all GET endpoints are read-only.
            // See: CookieUtils.setCookie() sets SameSite=Lax on all auth cookies.
            .csrf(AbstractHttpConfigurer::disable)

            // CORS: Allow frontend origin to make API calls
            .cors(cors -> {})

            // Stateless session: No server-side session storage
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // CORS preflight requests - must be allowed before other rules
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                // ASYNC dispatch (SSE continuations) - already authenticated on initial request
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()

                // Public endpoints: OAuth callbacks, health checks
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/internal/**").permitAll()

                // Public planner endpoints (no auth required for config and browsing)
                .requestMatchers("/api/planner/md/config").permitAll()
                .requestMatchers("/api/planner/md/published").permitAll()
                .requestMatchers("/api/planner/md/published/{id}").permitAll()
                .requestMatchers("/api/planner/md/recommended").permitAll()

                // Public user endpoints (association list for settings page)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/user/associations").permitAll()

                // Public comment endpoints (reading comments on published planners)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/planner/{plannerId}/comments").permitAll()

                // Public SSE for comment notifications (guests can subscribe)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/planner/{plannerId}/comments/events").permitAll()

                // Role-protected endpoints (ADMIN > MODERATOR > NORMAL hierarchy)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/moderation/**").hasRole("MODERATOR")

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Add JWT filter before Spring Security's authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // MDC runs after JWT so SecurityContext is populated and userId is available
            .addFilterAfter(mdcLoggingFilter, JwtAuthenticationFilter.class)

            // Security headers
            .headers(headers -> headers
                .xssProtection(xss -> xss.headerValue(
                    org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK
                ))
                .frameOptions(frame -> frame.deny())
                // HSTS: Force HTTPS for 1 year, include subdomains
                // Prevents SSL stripping attacks after first secure connection
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
            )

            // Exception handling: Return 401 with error details for unauthenticated access
            // CustomAuthenticationEntryPoint reads error code from request attribute (set by JwtAuthenticationFilter)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));

        return http.build();
    }
}
