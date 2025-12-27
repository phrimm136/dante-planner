# Security Guide – Enterprise Spring Security 6.x

Production-ready **Spring Security 6.x** patterns covering authentication, authorization, JWT tokens, password encoding, CORS, CSRF, method security, and security testing.

---

## Table of Contents

* [Security Architecture](#security-architecture)
* [Security Configuration](#security-configuration)
* [JWT Authentication](#jwt-authentication)
* [Password Encoding](#password-encoding)
* [Authorization Patterns](#authorization-patterns)
* [Method Security](#method-security)
* [CORS Configuration](#cors-configuration)
* [CSRF Protection](#csrf-protection)
* [Security Headers](#security-headers)
* [Rate Limiting](#rate-limiting)
* [Audit Logging](#audit-logging)
* [Security Testing](#security-testing)

---

## Security Architecture

### Security Filter Chain

```
HTTP Request
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  Security Filter Chain                                   │
├─────────────────────────────────────────────────────────┤
│  1. CorsFilter                                          │
│  2. CsrfFilter                                          │
│  3. JwtAuthenticationFilter (custom)                    │
│  4. UsernamePasswordAuthenticationFilter                │
│  5. ExceptionTranslationFilter                          │
│  6. AuthorizationFilter                                 │
└─────────────────────────────────────────────────────────┘
    │
    ▼
Controller / WebSocket Handler
```

### Security Principles

| Principle | Description |
|-----------|-------------|
| **Defense in Depth** | Multiple layers of security controls |
| **Least Privilege** | Grant minimum necessary permissions |
| **Fail Securely** | Deny access on error, not allow |
| **Secure by Default** | Restrictive defaults, explicit permissions |
| **Input Validation** | Validate all external input |

---

## Security Configuration

### Main Security Configuration (Spring Security 6.x)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CustomAuthenticationEntryPoint authEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/public/**", "/api/webhooks/**")
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // WebSocket endpoints
                .requestMatchers("/ws/**").permitAll()

                // Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; frame-ancestors 'none';")
                )
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.disable()) // Modern browsers use CSP
                .contentTypeOptions(Customizer.withDefaults())
            )
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
            "https://*.example.com",
            "http://localhost:3000"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Total-Count", "X-Page-Number"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### Custom Authentication Entry Point

```java
@Component
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        log.warn("Authentication failed for request {}: {}",
            request.getRequestURI(), authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiError error = ApiError.builder()
            .code("AUTHENTICATION_REQUIRED")
            .message("Authentication is required to access this resource")
            .path(request.getRequestURI())
            .timestamp(Instant.now())
            .build();

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
```

### Custom Access Denied Handler

```java
@Component
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Access denied for request {} by user {}: {}",
            request.getRequestURI(),
            getCurrentUsername(),
            accessDeniedException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApiError error = ApiError.builder()
            .code("ACCESS_DENIED")
            .message("You don't have permission to access this resource")
            .path(request.getRequestURI())
            .timestamp(Instant.now())
            .build();

        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
```

---

## JWT Authentication

### JWT Token Provider

```java
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-validity:3600000}") long accessTokenValidity,
            @Value("${security.jwt.refresh-token-validity:604800000}") long refreshTokenValidity) {

        // Ensure key is at least 256 bits for HS256
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
    }

    /**
     * Generate access token for user.
     */
    public String generateAccessToken(UserPrincipal user) {
        return buildToken(user, accessTokenValidity, TokenType.ACCESS);
    }

    /**
     * Generate refresh token for user.
     */
    public String generateRefreshToken(UserPrincipal user) {
        return buildToken(user, refreshTokenValidity, TokenType.REFRESH);
    }

    private String buildToken(UserPrincipal user, long validity, TokenType tokenType) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(validity);

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", user.getRoles())
            .claim("type", tokenType.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * Validate token and extract claims.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
            throw new TokenExpiredException("Token has expired");
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token format");
        } catch (SecurityException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token signature");
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Extract user ID from token.
     */
    public Long extractUserId(String token) {
        Claims claims = validateToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        } catch (TokenExpiredException e) {
            return true;
        }
    }

    /**
     * Check if token is a refresh token.
     */
    public boolean isRefreshToken(String token) {
        Claims claims = validateToken(token);
        String type = claims.get("type", String.class);
        return TokenType.REFRESH.name().equals(type);
    }

    public enum TokenType {
        ACCESS, REFRESH
    }
}
```

### JWT Authentication Filter

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Claims claims = tokenProvider.validateToken(token);

                // Don't allow refresh tokens for regular API access
                String tokenType = claims.get("type", String.class);
                if (JwtTokenProvider.TokenType.REFRESH.name().equals(tokenType)) {
                    log.warn("Refresh token used for API access");
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = Long.parseLong(claims.getSubject());
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId.toString());

                if (userDetails != null && userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                        );

                    authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authenticated user: {}", userId);
                }
            }
        } catch (TokenExpiredException e) {
            log.debug("Token expired for request: {}", request.getRequestURI());
        } catch (InvalidTokenException e) {
            log.debug("Invalid token for request: {}", request.getRequestURI());
        } catch (Exception e) {
            log.error("Authentication error", e);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        // Fallback to cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/public/") ||
               path.startsWith("/api/auth/login") ||
               path.startsWith("/api/auth/register") ||
               path.startsWith("/actuator/health");
    }
}
```

### User Principal

```java
@Getter
@Builder
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final Set<String> roles;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;

    public static UserPrincipal fromUser(User user) {
        return UserPrincipal.builder()
            .id(user.getId())
            .email(user.getEmail())
            .password(user.getPassword())
            .roles(user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet()))
            .enabled(user.isEnabled())
            .accountNonExpired(!user.isExpired())
            .accountNonLocked(!user.isLocked())
            .credentialsNonExpired(!user.isCredentialsExpired())
            .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return email;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
```

### Authentication Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticate user and return tokens.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.email(),
                    request.password()
                )
            );

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            String accessToken = tokenProvider.generateAccessToken(principal);
            String refreshToken = tokenProvider.generateRefreshToken(principal);

            // Store refresh token
            saveRefreshToken(principal.getId(), refreshToken);

            log.info("User logged in: {}", principal.getEmail());

            return new AuthResponse(accessToken, refreshToken, principal.getId());

        } catch (BadCredentialsException e) {
            log.warn("Login failed for email: {}", request.email());
            throw new AuthenticationException("Invalid email or password");
        } catch (DisabledException e) {
            log.warn("Login attempt for disabled account: {}", request.email());
            throw new AuthenticationException("Account is disabled");
        } catch (LockedException e) {
            log.warn("Login attempt for locked account: {}", request.email());
            throw new AuthenticationException("Account is locked");
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        // Validate refresh token
        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        Long userId = tokenProvider.extractUserId(refreshToken);

        // Verify refresh token is stored and not revoked
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (storedToken.isRevoked() || storedToken.isExpired()) {
            throw new InvalidTokenException("Refresh token is invalid");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new InvalidTokenException("User not found"));

        UserPrincipal principal = UserPrincipal.fromUser(user);

        String newAccessToken = tokenProvider.generateAccessToken(principal);
        String newRefreshToken = tokenProvider.generateRefreshToken(principal);

        // Rotate refresh token
        storedToken.revoke();
        saveRefreshToken(userId, newRefreshToken);

        log.debug("Token refreshed for user: {}", userId);

        return new AuthResponse(newAccessToken, newRefreshToken, userId);
    }

    /**
     * Logout user by revoking refresh token.
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
            .ifPresent(token -> {
                token.revoke();
                log.info("User logged out: {}", token.getUserId());
            });
    }

    /**
     * Revoke all refresh tokens for user (logout from all devices).
     */
    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All sessions revoked for user: {}", userId);
    }

    private void saveRefreshToken(Long userId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
            .userId(userId)
            .token(token)
            .expiresAt(Instant.now().plusMillis(604800000)) // 7 days
            .build();
        refreshTokenRepository.save(refreshToken);
    }
}

// DTOs
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}

public record AuthResponse(
    String accessToken,
    String refreshToken,
    Long userId
) {}
```

### Auth Controller

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.login(request);

        // Set refresh token as HttpOnly cookie
        addRefreshTokenCookie(response, authResponse.refreshToken());

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        UserDto user = userService.register(request);
        AuthResponse authResponse = authService.login(
            new LoginRequest(request.email(), request.password())
        );

        addRefreshTokenCookie(response, authResponse.refreshToken());

        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest bodyRequest,
            HttpServletResponse response) {

        String refreshToken = cookieToken != null ? cookieToken :
            (bodyRequest != null ? bodyRequest.refreshToken() : null);

        if (refreshToken == null) {
            throw new InvalidTokenException("Refresh token is required");
        }

        AuthResponse authResponse = authService.refreshToken(refreshToken);

        addRefreshTokenCookie(response, authResponse.refreshToken());

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        // Clear cookies
        clearAuthCookies(response);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal UserPrincipal user,
            HttpServletResponse response) {

        authService.logoutAll(user.getId());
        clearAuthCookies(response);

        return ResponseEntity.noContent().build();
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(Duration.ofDays(7))
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(0)
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
```

---

## Password Encoding

### Password Encoder Configuration

```java
@Configuration
public class PasswordConfig {

    /**
     * BCrypt with strength 12 (2^12 iterations).
     * Balance between security and performance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Alternative: Argon2 (recommended for new applications).
     */
    @Bean
    @Profile("argon2")
    public PasswordEncoder argon2PasswordEncoder() {
        return new Argon2PasswordEncoder(
            16,     // Salt length
            32,     // Hash length
            1,      // Parallelism
            65536,  // Memory (64MB)
            3       // Iterations
        );
    }

    /**
     * Delegating encoder for password migration.
     * Supports multiple encoding schemes.
     */
    @Bean
    @Profile("migration")
    public PasswordEncoder delegatingPasswordEncoder() {
        String encodingId = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        encoders.put("argon2", new Argon2PasswordEncoder(16, 32, 1, 65536, 3));
        encoders.put("sha256", new StandardPasswordEncoder()); // Legacy

        return new DelegatingPasswordEncoder(encodingId, encoders);
    }
}
```

### Password Validation

```java
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
@Documented
public @interface StrongPassword {
    String message() default "Password does not meet security requirements";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    int minLength() default 8;
    int maxLength() default 72; // BCrypt limit
    boolean requireUppercase() default true;
    boolean requireLowercase() default true;
    boolean requireDigit() default true;
    boolean requireSpecial() default true;
}

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private int minLength;
    private int maxLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigit;
    private boolean requireSpecial;

    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

    @Override
    public void initialize(StrongPassword annotation) {
        this.minLength = annotation.minLength();
        this.maxLength = annotation.maxLength();
        this.requireUppercase = annotation.requireUppercase();
        this.requireLowercase = annotation.requireLowercase();
        this.requireDigit = annotation.requireDigit();
        this.requireSpecial = annotation.requireSpecial();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) return true; // Let @NotBlank handle null

        List<String> violations = new ArrayList<>();

        if (password.length() < minLength) {
            violations.add("at least " + minLength + " characters");
        }
        if (password.length() > maxLength) {
            violations.add("at most " + maxLength + " characters");
        }
        if (requireUppercase && !password.matches(".*[A-Z].*")) {
            violations.add("an uppercase letter");
        }
        if (requireLowercase && !password.matches(".*[a-z].*")) {
            violations.add("a lowercase letter");
        }
        if (requireDigit && !password.matches(".*\\d.*")) {
            violations.add("a digit");
        }
        if (requireSpecial && !containsSpecialChar(password)) {
            violations.add("a special character");
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Password must contain " + String.join(", ", violations)
            ).addConstraintViolation();
            return false;
        }

        return true;
    }

    private boolean containsSpecialChar(String password) {
        for (char c : password.toCharArray()) {
            if (SPECIAL_CHARS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}
```

---

## Authorization Patterns

### Role-Based Access Control

```java
/**
 * User roles enumeration.
 */
public enum RoleName {
    USER,
    MODERATOR,
    ADMIN,
    SUPER_ADMIN
}

/**
 * Role entity with permissions.
 */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleName name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public boolean hasPermission(String permission) {
        return permissions.stream()
            .anyMatch(p -> p.getName().equals(permission));
    }
}

/**
 * Permission entity.
 */
@Entity
@Table(name = "permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "users:read", "posts:write", "admin:delete"

    @Column
    private String description;
}
```

### Permission-Based Security Expressions

```java
@Component("securityService")
@RequiredArgsConstructor
public class SecurityExpressionService {

    private final PostRepository postRepository;
    private final OrganizationMemberRepository memberRepository;

    /**
     * Check if current user owns the resource.
     */
    public boolean isOwner(Long resourceOwnerId) {
        UserPrincipal user = getCurrentUser();
        return user != null && user.getId().equals(resourceOwnerId);
    }

    /**
     * Check if current user can modify the post.
     */
    public boolean canModifyPost(Long postId) {
        UserPrincipal user = getCurrentUser();
        if (user == null) return false;

        // Admins can modify any post
        if (user.hasRole("ADMIN")) return true;

        // Check ownership
        return postRepository.findById(postId)
            .map(post -> post.getAuthorId().equals(user.getId()))
            .orElse(false);
    }

    /**
     * Check if current user is member of organization.
     */
    public boolean isMemberOf(Long organizationId) {
        UserPrincipal user = getCurrentUser();
        if (user == null) return false;

        return memberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId());
    }

    /**
     * Check if current user has role in organization.
     */
    public boolean hasOrgRole(Long organizationId, String role) {
        UserPrincipal user = getCurrentUser();
        if (user == null) return false;

        return memberRepository.findByOrganizationIdAndUserId(organizationId, user.getId())
            .map(member -> member.getRole().equals(role))
            .orElse(false);
    }

    private UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) auth.getPrincipal();
        }
        return null;
    }
}
```

---

## Method Security

### Using @PreAuthorize and @PostAuthorize

```java
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    /**
     * Only authenticated users can create posts.
     */
    @PreAuthorize("isAuthenticated()")
    public PostDto createPost(CreatePostRequest request, UserPrincipal author) {
        Post post = Post.create(request.title(), request.content(), author.getId());
        return PostDto.from(postRepository.persist(post));
    }

    /**
     * Only post owner or admin can update.
     */
    @PreAuthorize("@securityService.canModifyPost(#postId) or hasRole('ADMIN')")
    public PostDto updatePost(Long postId, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post", postId));

        post.update(request.title(), request.content());
        return PostDto.from(post);
    }

    /**
     * Only post owner or admin can delete.
     */
    @PreAuthorize("@securityService.canModifyPost(#postId) or hasRole('ADMIN')")
    public void deletePost(Long postId) {
        postRepository.deleteById(postId);
    }

    /**
     * Filter returned data based on user permissions.
     */
    @PostAuthorize("returnObject.authorId == authentication.principal.id or hasRole('ADMIN')")
    public PostDto getPostWithDrafts(Long postId) {
        return postRepository.findById(postId)
            .map(PostDto::from)
            .orElseThrow(() -> new NotFoundException("Post", postId));
    }

    /**
     * Filter collection based on permissions.
     */
    @PostFilter("filterObject.published or filterObject.authorId == authentication.principal.id")
    public List<PostDto> getUserPosts(Long userId) {
        return postRepository.findByAuthorId(userId).stream()
            .map(PostDto::from)
            .toList();
    }
}
```

### Custom Security Annotations

```java
/**
 * Meta-annotation for admin-only access.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {}

/**
 * Meta-annotation for resource owner access.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@securityService.isOwner(#userId) or hasRole('ADMIN')")
public @interface OwnerOrAdmin {}

/**
 * Meta-annotation for organization member access.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@securityService.isMemberOf(#organizationId)")
public @interface OrgMemberOnly {}

// Usage
@Service
public class UserService {

    @AdminOnly
    public void deleteUser(Long userId) {
        // Only admins can delete users
    }

    @OwnerOrAdmin
    public UserDto updateUser(Long userId, UpdateUserRequest request) {
        // Only owner or admin can update
    }
}
```

---

## CORS Configuration

### Production CORS Setup

```java
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Use patterns for flexible origin matching
        config.setAllowedOriginPatterns(allowedOrigins);

        // Allowed HTTP methods
        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Allowed headers
        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "X-CSRF-TOKEN"
        ));

        // Headers exposed to client
        config.setExposedHeaders(List.of(
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size",
            "Location"
        ));

        // Allow credentials (cookies, auth headers)
        config.setAllowCredentials(true);

        // Preflight cache duration
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/ws/**", config);

        return source;
    }
}
```

---

## CSRF Protection

### CSRF Configuration for SPA

```java
@Configuration
public class CsrfConfig {

    /**
     * CSRF token repository using cookies.
     * Token is accessible to JavaScript for SPA applications.
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }
}

// In SecurityConfig
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfTokenRepository())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            // Exclude stateless API endpoints
            .ignoringRequestMatchers(
                "/api/auth/login",
                "/api/auth/register",
                "/api/webhooks/**"
            )
        )
        // ... other config
        .build();
}
```

---

## Security Headers

### Security Headers Configuration

```java
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .headers(headers -> headers
                // Content Security Policy
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(buildCspPolicy())
                )
                // Prevent clickjacking
                .frameOptions(frame -> frame.deny())
                // Prevent MIME type sniffing
                .contentTypeOptions(Customizer.withDefaults())
                // Referrer policy
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                // Permissions policy
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=()")
                )
            )
            .build();
    }

    private String buildCspPolicy() {
        return String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: https:",
            "font-src 'self'",
            "connect-src 'self' wss:",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'"
        );
    }
}
```

---

## Rate Limiting

### Rate Limiting with Bucket4j

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientId = resolveClientId(request);
        String endpoint = request.getRequestURI();

        RateLimitResult result = rateLimitService.tryConsume(clientId, endpoint);

        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetAt()));

        if (!result.allowed()) {
            log.warn("Rate limit exceeded for client: {}, endpoint: {}", clientId, endpoint);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));

            ApiError error = ApiError.builder()
                .code("RATE_LIMIT_EXCEEDED")
                .message("Too many requests. Please retry after " + result.retryAfterSeconds() + " seconds")
                .timestamp(Instant.now())
                .build();

            new ObjectMapper().writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientId(HttpServletRequest request) {
        // Try authenticated user first
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal user) {
            return "user:" + user.getId();
        }

        // Fall back to IP address
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}

@Service
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final int REQUESTS_PER_MINUTE = 60;
    private static final int REQUESTS_PER_HOUR = 1000;

    public RateLimitResult tryConsume(String clientId, String endpoint) {
        Bucket bucket = buckets.computeIfAbsent(clientId, this::createBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        return new RateLimitResult(
            probe.isConsumed(),
            REQUESTS_PER_MINUTE,
            probe.getRemainingTokens(),
            Instant.now().plusSeconds(probe.getNanosToWaitForRefill() / 1_000_000_000),
            probe.getNanosToWaitForRefill() / 1_000_000_000
        );
    }

    private Bucket createBucket(String clientId) {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(
                REQUESTS_PER_MINUTE,
                Refill.greedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
            ))
            .addLimit(Bandwidth.classic(
                REQUESTS_PER_HOUR,
                Refill.greedy(REQUESTS_PER_HOUR, Duration.ofHours(1))
            ))
            .build();
    }
}

public record RateLimitResult(
    boolean allowed,
    long limit,
    long remaining,
    Instant resetAt,
    long retryAfterSeconds
) {}
```

---

## Audit Logging

### Security Audit Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {

    private final SecurityAuditRepository auditRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Log security event.
     */
    @Async
    public void logEvent(SecurityEventType eventType, String userId, String details) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
            .eventType(eventType)
            .userId(userId)
            .details(details)
            .ipAddress(getCurrentIpAddress())
            .userAgent(getCurrentUserAgent())
            .timestamp(Instant.now())
            .build();

        auditRepository.save(auditLog);

        log.info("Security event: type={}, user={}, details={}",
            eventType, userId, details);
    }

    /**
     * Log authentication events.
     */
    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        logEvent(SecurityEventType.LOGIN_SUCCESS, username, "Successful login");
    }

    @EventListener
    public void handleAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String reason = event.getException().getMessage();
        logEvent(SecurityEventType.LOGIN_FAILURE, username, "Failed login: " + reason);
    }

    @EventListener
    public void handleAuthorizationFailure(AuthorizationDeniedEvent event) {
        Authentication auth = event.getAuthentication().get();
        String username = auth != null ? auth.getName() : "anonymous";
        logEvent(SecurityEventType.ACCESS_DENIED, username,
            "Access denied to: " + event.getAuthorizationDecision());
    }

    private String getCurrentIpAddress() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            return forwardedFor != null ? forwardedFor.split(",")[0] : request.getRemoteAddr();
        }
        return "unknown";
    }

    private String getCurrentUserAgent() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest().getHeader("User-Agent");
        }
        return "unknown";
    }
}

public enum SecurityEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    ACCESS_DENIED,
    PASSWORD_CHANGE,
    PASSWORD_RESET_REQUEST,
    PASSWORD_RESET_COMPLETE,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    TOKEN_REVOKED,
    SUSPICIOUS_ACTIVITY
}

@Entity
@Table(name = "security_audit_logs",
    indexes = {
        @Index(name = "idx_audit_user_id", columnList = "userId"),
        @Index(name = "idx_audit_event_type", columnList = "eventType"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityEventType eventType;

    @Column(nullable = false)
    private String userId;

    @Column(length = 1000)
    private String details;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column(nullable = false)
    private Instant timestamp;
}
```

---

## Security Testing

### Unit Testing Security Components

```java
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        String secret = "test-secret-key-that-is-at-least-32-characters-long";
        tokenProvider = new JwtTokenProvider(secret, 3600000, 604800000);
    }

    @Test
    void generateAccessToken_shouldCreateValidToken() {
        UserPrincipal user = UserPrincipal.builder()
            .id(1L)
            .email("test@example.com")
            .roles(Set.of("USER"))
            .build();

        String token = tokenProvider.generateAccessToken(user);

        assertThat(token).isNotBlank();

        Claims claims = tokenProvider.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email")).isEqualTo("test@example.com");
        assertThat(claims.get("type")).isEqualTo("ACCESS");
    }

    @Test
    void validateToken_withExpiredToken_shouldThrow() {
        // Create provider with 0ms validity
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
            "test-secret-key-that-is-at-least-32-characters-long", 0, 0);

        UserPrincipal user = UserPrincipal.builder()
            .id(1L)
            .email("test@example.com")
            .roles(Set.of("USER"))
            .build();

        String token = shortLivedProvider.generateAccessToken(user);

        assertThatThrownBy(() -> shortLivedProvider.validateToken(token))
            .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void validateToken_withInvalidToken_shouldThrow() {
        assertThatThrownBy(() -> tokenProvider.validateToken("invalid.token.here"))
            .isInstanceOf(InvalidTokenException.class);
    }
}
```

### Integration Testing Security

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
            .email("test@example.com")
            .password(passwordEncoder.encode("password123"))
            .roles(Set.of(Role.USER))
            .enabled(true)
            .build());
    }

    @Test
    void accessProtectedEndpoint_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void accessProtectedEndpoint_withValidToken_shouldReturn200() throws Exception {
        String token = generateTestToken(testUser);

        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void accessAdminEndpoint_withUserRole_shouldReturn403() throws Exception {
        String token = generateTestToken(testUser);

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void login_withValidCredentials_shouldReturnTokens() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void login_withInvalidCredentials_shouldReturn401() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String generateTestToken(User user) {
        UserPrincipal principal = UserPrincipal.fromUser(user);
        return tokenProvider.generateAccessToken(principal);
    }
}
```

### Testing Method Security

```java
@SpringBootTest
@WithMockUser(username = "user@example.com", roles = {"USER"})
class MethodSecurityTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Test
    void createPost_asUser_shouldSucceed() {
        CreatePostRequest request = new CreatePostRequest("Title", "Content");

        assertThatCode(() -> postService.createPost(request))
            .doesNotThrowAnyException();
    }

    @Test
    @WithAnonymousUser
    void createPost_asAnonymous_shouldThrow() {
        CreatePostRequest request = new CreatePostRequest("Title", "Content");

        assertThatThrownBy(() -> postService.createPost(request))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updatePost_asNonOwner_shouldThrow() {
        // Create post as different user
        Post post = postRepository.save(Post.create("Title", "Content", 999L));

        UpdatePostRequest request = new UpdatePostRequest("New Title", "New Content");

        assertThatThrownBy(() -> postService.updatePost(post.getId(), request))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void updatePost_asAdmin_shouldSucceed() {
        Post post = postRepository.save(Post.create("Title", "Content", 999L));

        UpdatePostRequest request = new UpdatePostRequest("New Title", "New Content");

        assertThatCode(() -> postService.updatePost(post.getId(), request))
            .doesNotThrowAnyException();
    }
}
```

### Custom Security Test Annotations

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockUserPrincipalSecurityContextFactory.class)
public @interface WithMockUserPrincipal {
    long id() default 1L;
    String email() default "test@example.com";
    String[] roles() default {"USER"};
}

public class WithMockUserPrincipalSecurityContextFactory
        implements WithSecurityContextFactory<WithMockUserPrincipal> {

    @Override
    public SecurityContext createSecurityContext(WithMockUserPrincipal annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        UserPrincipal principal = UserPrincipal.builder()
            .id(annotation.id())
            .email(annotation.email())
            .roles(Set.of(annotation.roles()))
            .enabled(true)
            .accountNonExpired(true)
            .accountNonLocked(true)
            .credentialsNonExpired(true)
            .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );

        context.setAuthentication(auth);
        return context;
    }
}

// Usage
@Test
@WithMockUserPrincipal(id = 1L, email = "admin@example.com", roles = {"ADMIN"})
void adminOperation_shouldSucceed() {
    // Test with custom principal
}
```

---

## Summary: Security Rules

| Rule | Description |
|------|-------------|
| **Stateless JWT** | Use JWT for stateless authentication |
| **Secure Tokens** | Store refresh tokens securely, rotate on use |
| **Password Encoding** | BCrypt (12 rounds) or Argon2 |
| **Method Security** | Use `@PreAuthorize` for fine-grained control |
| **CORS** | Explicit allowed origins, no wildcards in production |
| **CSRF** | Enable for browser clients, cookie-based token |
| **Security Headers** | CSP, X-Frame-Options, X-Content-Type-Options |
| **Rate Limiting** | Per-user and per-IP limits |
| **Audit Logging** | Log all security events |
| **Testing** | Unit test components, integration test flows |

---

**Related Files:**

* [SKILL.md](../SKILL.md) - Main skill guide
* [async-and-errors.md](async-and-errors.md) - Exception handling
* [configuration.md](configuration.md) - Configuration patterns
* [websocket-guide.md](websocket-guide.md) - WebSocket security
