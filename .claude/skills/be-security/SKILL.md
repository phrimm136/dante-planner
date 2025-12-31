---
name: be-security
description: Spring Security patterns. Authentication, authorization, JWT, CORS.
---

# Backend Security Patterns

## Rules

- **No wildcard CORS in prod** - Explicit origin list
- **Secrets in env vars** - Never hardcode
- **Rate limit public endpoints** - Prevent abuse
- **Verify ownership server-side** - Don't trust client IDs

## Forbidden → Use Instead

| Forbidden | Use Instead |
|-----------|-------------|
| `allowedOrigins("*")` | Explicit origin list |
| Hardcoded secrets | `@Value("${jwt.secret}")` |
| No rate limiting | Bucket4j or Spring rate limiter |
| Trust client-provided IDs | Server-side ownership check |

## Security Config Template

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://example.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

## JWT Filter Template

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) {
        String token = extractToken(request);
        if (token != null && jwtTokenProvider.validate(token)) {
            Authentication auth = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

## Reference

- Pattern: `SecurityConfig.java`
- Why: `docs/learning/backend-patterns.md`
