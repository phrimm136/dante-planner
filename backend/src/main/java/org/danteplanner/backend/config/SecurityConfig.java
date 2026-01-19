package org.danteplanner.backend.config;

import org.danteplanner.backend.security.CustomAuthenticationEntryPoint;
import org.danteplanner.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import jakarta.servlet.DispatcherType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
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

                // Public planner endpoints (no auth required for config and browsing)
                .requestMatchers("/api/planner/md/config").permitAll()
                .requestMatchers("/api/planner/md/published").permitAll()
                .requestMatchers("/api/planner/md/published/{id}").permitAll()
                .requestMatchers("/api/planner/md/recommended").permitAll()

                // Public view recording endpoint (anonymous tracking allowed)
                // Note: Spring Security 7 uses PathPattern syntax - {id} for path variables, not Ant-style *
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/planner/md/{id}/view").permitAll()

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

            // Security headers
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
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
