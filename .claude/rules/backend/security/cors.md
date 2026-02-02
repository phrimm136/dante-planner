---
paths:
  - "backend/**/security/**/*.java"
  - "backend/**/config/SecurityConfig.java"
  - "backend/**/config/*Security*.java"
---

# CORS Configuration Patterns

## Mandatory Rules

- **No wildcard CORS in prod** - Explicit origin list
- **Rate limit public endpoints** - Prevent abuse

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| `allowedOrigins("*")` | Explicit origin list |
| No rate limiting | Bucket4j or Spring rate limiter |

## Template

```java
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
```

## Environment-Specific Origins

| Environment | Allowed Origins |
|-------------|----------------|
| Development | `http://localhost:3000`, `http://localhost:5173` |
| Staging | `https://staging.example.com` |
| Production | `https://example.com` |
