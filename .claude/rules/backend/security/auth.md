---
paths:
  - "backend/**/security/**/*.java"
  - "backend/**/config/SecurityConfig.java"
  - "backend/**/filter/**/*Auth*.java"
---

# Authentication & Authorization Patterns

## Mandatory Rules

- **Secrets in env vars** - Never hardcode
- **Verify ownership server-side** - Don't trust client IDs
- **Skip JWT filter on ASYNC dispatch** - Prevents 403 on async
- **ThreadLocal SecureRandom** - Use above 100 req/sec to prevent contention
- **PEM key strength validation** - Verify minimum 2048-bit RSA at startup

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| Hardcoded secrets | `@Value("${jwt.secret}")` |
| Trust client-provided IDs | Server-side ownership check |
| JWT filter on ASYNC dispatch | `.dispatcherTypeMatchers(ASYNC).permitAll()` |

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
}
```

**Reference:** `SecurityConfig.java`

## Crypto Performance Patterns

**ThreadLocal SecureRandom:** Use ThreadLocal.withInitial(SecureRandom::new) above 100 req/sec to prevent contention. Shared instance is synchronized.

**PEM Key Validation:** Verify RSA key strength (minimum 2048 bits) at startup using RSAPrivateKey.getModulus().bitLength(). Fail-fast prevents weak keys.

**Reference:** `JwtTokenService.java`, `JwtProperties.java`
