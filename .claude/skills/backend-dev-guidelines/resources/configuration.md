# Configuration Management – Enterprise Spring Boot Configuration

Production-ready guide to managing configuration using **@ConfigurationProperties**, profiles, secrets management, and validation in Spring Boot applications.

## Table of Contents

* [Configuration Architecture](#configuration-architecture)
* [Type-Safe Configuration Properties](#type-safe-configuration-properties)
* [Configuration Validation](#configuration-validation)
* [Profile-Based Configuration](#profile-based-configuration)
* [Secrets Management](#secrets-management)
* [Feature Flags](#feature-flags)
* [Configuration Refresh](#configuration-refresh)
* [Testing Configuration](#testing-configuration)

---

## Configuration Architecture

### Configuration Principles

| Principle | Description |
|-----------|-------------|
| **Type-Safe** | Use `@ConfigurationProperties`, never `System.getenv()` |
| **Fail-Fast** | Validate all required config at startup |
| **Environment-Agnostic** | Same code, different config per environment |
| **Secure** | Secrets via environment variables or vault |
| **Documented** | Self-documenting with metadata |

### What NOT to Do

```java
// ❌ NEVER: Direct environment access
String dbHost = System.getenv("DB_HOST");

// ❌ NEVER: Scattered @Value annotations
@Value("${app.timeout}")
private int timeout;

// ❌ NEVER: Magic strings in code
if ("production".equals(System.getenv("ENV"))) { ... }
```

### What TO Do

```java
// ✅ ALWAYS: Type-safe configuration
@Component
@RequiredArgsConstructor
public class UserService {
    private final AppConfig config;

    public void process() {
        Duration timeout = config.getHttp().getConnectTimeout();
        String baseUrl = config.getServices().getUserService().getBaseUrl();
    }
}
```

---

## Type-Safe Configuration Properties

### Root Configuration Class

```java
@Configuration
@EnableConfigurationProperties({
    AppConfig.class,
    SecurityConfig.class,
    IntegrationConfig.class
})
public class ConfigurationModule {
}
```

### Application Configuration

```java
/**
 * Root application configuration.
 * All application settings are accessed through this class.
 */
@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppConfig {

    /**
     * Application name for identification.
     */
    @NotBlank
    private String name;

    /**
     * Application version from build info.
     */
    private String version = "unknown";

    /**
     * Environment identifier.
     */
    @NotNull
    private Environment environment = Environment.DEVELOPMENT;

    /**
     * Database configuration.
     */
    @Valid
    @NotNull
    private DatabaseConfig database = new DatabaseConfig();

    /**
     * HTTP client configuration.
     */
    @Valid
    @NotNull
    private HttpConfig http = new HttpConfig();

    /**
     * External service endpoints.
     */
    @Valid
    private ServicesConfig services = new ServicesConfig();

    /**
     * Feature flags.
     */
    @Valid
    private FeaturesConfig features = new FeaturesConfig();

    public enum Environment {
        DEVELOPMENT, STAGING, PRODUCTION
    }
}
```

### Nested Configuration Classes

```java
/**
 * Database connection configuration.
 */
@Getter
@Setter
public class DatabaseConfig {

    @NotBlank
    private String host = "localhost";

    @Min(1)
    @Max(65535)
    private int port = 5432;

    @NotBlank
    private String name;

    private String username;

    private String password;

    /**
     * Connection pool configuration.
     */
    @Valid
    private PoolConfig pool = new PoolConfig();

    @Getter
    @Setter
    public static class PoolConfig {

        @Min(1)
        private int minimumIdle = 5;

        @Min(1)
        private int maximumPoolSize = 20;

        @DurationMin(seconds = 1)
        private Duration connectionTimeout = Duration.ofSeconds(30);

        @DurationMin(seconds = 1)
        private Duration idleTimeout = Duration.ofMinutes(10);

        @DurationMin(minutes = 1)
        private Duration maxLifetime = Duration.ofMinutes(30);
    }

    /**
     * Builds JDBC URL from configuration.
     */
    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, name);
    }
}
```

```java
/**
 * HTTP client configuration.
 */
@Getter
@Setter
public class HttpConfig {

    @DurationMin(millis = 100)
    private Duration connectTimeout = Duration.ofSeconds(10);

    @DurationMin(millis = 100)
    private Duration readTimeout = Duration.ofSeconds(30);

    @DurationMin(millis = 100)
    private Duration writeTimeout = Duration.ofSeconds(30);

    @Min(1)
    @Max(100)
    private int maxConnections = 50;

    @Min(1)
    @Max(50)
    private int maxConnectionsPerRoute = 20;

    /**
     * Retry configuration.
     */
    @Valid
    private RetryConfig retry = new RetryConfig();

    @Getter
    @Setter
    public static class RetryConfig {

        private boolean enabled = true;

        @Min(0)
        @Max(10)
        private int maxAttempts = 3;

        @DurationMin(millis = 10)
        private Duration initialInterval = Duration.ofMillis(100);

        @DecimalMin("1.0")
        @DecimalMax("5.0")
        private double multiplier = 2.0;

        @DurationMin(millis = 100)
        private Duration maxInterval = Duration.ofSeconds(10);
    }
}
```

```java
/**
 * External service endpoints configuration.
 */
@Getter
@Setter
public class ServicesConfig {

    @Valid
    private ServiceEndpoint userService = new ServiceEndpoint();

    @Valid
    private ServiceEndpoint notificationService = new ServiceEndpoint();

    @Valid
    private ServiceEndpoint paymentService = new ServiceEndpoint();

    @Getter
    @Setter
    public static class ServiceEndpoint {

        @URL
        private String baseUrl;

        private String apiKey;

        @DurationMin(millis = 100)
        private Duration timeout = Duration.ofSeconds(30);

        private boolean enabled = true;
    }
}
```

---

## Configuration Validation

### Built-in Validation Constraints

```java
@ConfigurationProperties(prefix = "app.security")
@Validated
@Getter
@Setter
public class SecurityConfig {

    /**
     * JWT signing secret (minimum 256 bits).
     */
    @NotBlank
    @Size(min = 32, message = "JWT secret must be at least 32 characters (256 bits)")
    private String jwtSecret;

    /**
     * Token expiration time.
     */
    @NotNull
    @DurationMin(minutes = 1)
    @DurationMax(days = 30)
    private Duration tokenExpiration = Duration.ofHours(24);

    /**
     * Refresh token expiration.
     */
    @NotNull
    @DurationMin(hours = 1)
    private Duration refreshTokenExpiration = Duration.ofDays(7);

    /**
     * CORS allowed origins.
     */
    @NotEmpty
    private List<@URL String> allowedOrigins = List.of("http://localhost:3000");

    /**
     * Rate limiting configuration.
     */
    @Valid
    @NotNull
    private RateLimitConfig rateLimit = new RateLimitConfig();

    @Getter
    @Setter
    public static class RateLimitConfig {

        private boolean enabled = true;

        @Min(1)
        private int requestsPerMinute = 100;

        @Min(1)
        private int requestsPerHour = 1000;
    }
}
```

### Custom Validation Constraints

```java
/**
 * Validates that a Duration is within specified bounds.
 */
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = DurationRangeValidator.class)
public @interface DurationRange {
    String message() default "Duration must be between {min} and {max}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    String min() default "PT0S";
    String max() default "P365D";
}

public class DurationRangeValidator implements ConstraintValidator<DurationRange, Duration> {

    private Duration min;
    private Duration max;

    @Override
    public void initialize(DurationRange annotation) {
        this.min = Duration.parse(annotation.min());
        this.max = Duration.parse(annotation.max());
    }

    @Override
    public boolean isValid(Duration value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return !value.minus(min).isNegative() && !max.minus(value).isNegative();
    }
}
```

### Startup Validation

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationValidator implements ApplicationRunner {

    private final AppConfig appConfig;
    private final SecurityConfig securityConfig;
    private final Validator validator;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Validating application configuration...");

        validateConfig(appConfig, "app");
        validateConfig(securityConfig, "security");

        // Custom cross-property validation
        validateSecurityInProduction();

        log.info("Configuration validation completed successfully");
    }

    private void validateConfig(Object config, String name) {
        Set<ConstraintViolation<Object>> violations = validator.validate(config);

        if (!violations.isEmpty()) {
            String errors = violations.stream()
                .map(v -> String.format("  - %s: %s (value: %s)",
                    v.getPropertyPath(), v.getMessage(), v.getInvalidValue()))
                .collect(Collectors.joining("\n"));

            throw new ConfigurationException(
                String.format("Invalid %s configuration:\n%s", name, errors));
        }
    }

    private void validateSecurityInProduction() {
        if (appConfig.getEnvironment() == Environment.PRODUCTION) {
            if (securityConfig.getJwtSecret().startsWith("dev-")) {
                throw new ConfigurationException(
                    "Production environment cannot use development JWT secret");
            }

            if (!securityConfig.getRateLimit().isEnabled()) {
                log.warn("Rate limiting is disabled in production - this is not recommended");
            }
        }
    }
}
```

---

## Profile-Based Configuration

### Application YAML Structure

```yaml
# application.yml - Default/Development settings
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

app:
  name: my-application
  environment: DEVELOPMENT

  database:
    host: localhost
    port: 5432
    name: myapp_dev
    username: postgres
    password: postgres
    pool:
      minimum-idle: 2
      maximum-pool-size: 5

  http:
    connect-timeout: 10s
    read-timeout: 30s
    retry:
      enabled: true
      max-attempts: 3

  features:
    new-dashboard: true
    beta-features: true

security:
  jwt-secret: dev-secret-key-for-local-development-only
  token-expiration: 24h
  allowed-origins:
    - http://localhost:3000
    - http://localhost:5173
```

```yaml
# application-prod.yml - Production overrides
app:
  environment: PRODUCTION

  database:
    host: ${DB_HOST}
    port: ${DB_PORT:5432}
    name: ${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    pool:
      minimum-idle: 10
      maximum-pool-size: 50
      connection-timeout: 30s

  http:
    connect-timeout: 5s
    read-timeout: 15s
    max-connections: 100

  services:
    user-service:
      base-url: ${USER_SERVICE_URL}
      api-key: ${USER_SERVICE_API_KEY}
    notification-service:
      base-url: ${NOTIFICATION_SERVICE_URL}

  features:
    new-dashboard: ${FEATURE_NEW_DASHBOARD:false}
    beta-features: false

security:
  jwt-secret: ${JWT_SECRET}
  token-expiration: 1h
  refresh-token-expiration: 7d
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
  rate-limit:
    enabled: true
    requests-per-minute: 60
```

```yaml
# application-test.yml - Test settings
app:
  environment: DEVELOPMENT

  database:
    host: localhost
    port: 5433
    name: myapp_test

  http:
    connect-timeout: 1s
    read-timeout: 5s
    retry:
      enabled: false

  features:
    new-dashboard: true
    beta-features: true

security:
  jwt-secret: test-secret-key-for-testing-only-32chars
  token-expiration: 1h
```

### Profile Activation

```java
// Programmatic profile activation
@Configuration
public class ProfileConfig {

    @Bean
    @Profile("prod")
    public DataSource productionDataSource(AppConfig config) {
        // Production-specific datasource with connection pooling
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getDatabase().getJdbcUrl());
        hikari.setUsername(config.getDatabase().getUsername());
        hikari.setPassword(config.getDatabase().getPassword());
        hikari.setMinimumIdle(config.getDatabase().getPool().getMinimumIdle());
        hikari.setMaximumPoolSize(config.getDatabase().getPool().getMaximumPoolSize());
        return new HikariDataSource(hikari);
    }

    @Bean
    @Profile("!prod")
    public DataSource developmentDataSource(AppConfig config) {
        // Simpler datasource for development
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getDatabase().getJdbcUrl());
        hikari.setUsername(config.getDatabase().getUsername());
        hikari.setPassword(config.getDatabase().getPassword());
        hikari.setMaximumPoolSize(5);
        return new HikariDataSource(hikari);
    }
}
```

---

## Secrets Management

### Never Commit Secrets

```gitignore
# .gitignore
application-local.yml
application-prod.yml
.env
.env.*
*.pem
*.key
secrets/
```

### Environment Variable Binding

```yaml
# Use ${VAR_NAME} for required, ${VAR_NAME:default} for optional
app:
  database:
    password: ${DB_PASSWORD}  # Required - fails if not set
    host: ${DB_HOST:localhost}  # Optional with default
```

### Spring Cloud Vault Integration

```java
// build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-vault-config'
}
```

```yaml
# bootstrap.yml
spring:
  cloud:
    vault:
      enabled: ${VAULT_ENABLED:false}
      uri: ${VAULT_URI:http://localhost:8200}
      authentication: TOKEN
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
        application-name: my-application
```

### AWS Secrets Manager Integration

```java
@Configuration
@ConditionalOnProperty(name = "aws.secrets.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AwsSecretsConfig {

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
            .region(Region.of(System.getenv("AWS_REGION")))
            .build();
    }

    @Bean
    @Primary
    public AppConfig awsEnhancedAppConfig(
            AppConfig baseConfig,
            SecretsManagerClient secretsClient) {

        // Fetch secrets and enhance config
        String secretJson = getSecret(secretsClient, "my-app/prod");
        Map<String, String> secrets = parseSecrets(secretJson);

        baseConfig.getDatabase().setPassword(secrets.get("db_password"));
        baseConfig.getSecurity().setJwtSecret(secrets.get("jwt_secret"));

        return baseConfig;
    }

    private String getSecret(SecretsManagerClient client, String secretName) {
        GetSecretValueResponse response = client.getSecretValue(
            GetSecretValueRequest.builder()
                .secretId(secretName)
                .build()
        );
        return response.secretString();
    }
}
```

---

## Feature Flags

### Feature Configuration

```java
/**
 * Feature flags configuration.
 */
@Getter
@Setter
public class FeaturesConfig {

    /**
     * Enable new dashboard UI.
     */
    private boolean newDashboard = false;

    /**
     * Enable beta features for testing.
     */
    private boolean betaFeatures = false;

    /**
     * Enable experimental API endpoints.
     */
    private boolean experimentalApi = false;

    /**
     * A/B test configurations.
     */
    @Valid
    private Map<String, AbTestConfig> abTests = new HashMap<>();

    @Getter
    @Setter
    public static class AbTestConfig {

        private boolean enabled = false;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double percentage = 0.0;

        private Set<String> allowedUsers = new HashSet<>();
    }
}
```

### Feature Flag Service

```java
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final AppConfig appConfig;

    /**
     * Checks if a feature is enabled.
     */
    public boolean isEnabled(Feature feature) {
        return switch (feature) {
            case NEW_DASHBOARD -> appConfig.getFeatures().isNewDashboard();
            case BETA_FEATURES -> appConfig.getFeatures().isBetaFeatures();
            case EXPERIMENTAL_API -> appConfig.getFeatures().isExperimentalApi();
        };
    }

    /**
     * Checks if user is in A/B test variant.
     */
    public boolean isInAbTest(String testName, Long userId) {
        FeaturesConfig.AbTestConfig test = appConfig.getFeatures().getAbTests().get(testName);

        if (test == null || !test.isEnabled()) {
            return false;
        }

        // Check if user is explicitly allowed
        if (test.getAllowedUsers().contains(String.valueOf(userId))) {
            return true;
        }

        // Hash-based percentage allocation
        double hash = Math.abs(Objects.hash(testName, userId)) / (double) Integer.MAX_VALUE;
        return hash < test.getPercentage();
    }

    public enum Feature {
        NEW_DASHBOARD,
        BETA_FEATURES,
        EXPERIMENTAL_API
    }
}
```

### Using Feature Flags

```java
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final FeatureFlagService featureFlags;
    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<?> getDashboard(@AuthenticationPrincipal UserPrincipal user) {
        if (featureFlags.isEnabled(Feature.NEW_DASHBOARD)) {
            return ResponseEntity.ok(dashboardService.getNewDashboard(user.getId()));
        }
        return ResponseEntity.ok(dashboardService.getLegacyDashboard(user.getId()));
    }
}
```

---

## Configuration Refresh

### Spring Cloud Config Refresh

```java
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class ConfigRefreshController {

    private final ContextRefresher contextRefresher;
    private final AppConfig appConfig;

    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Set<String> refreshedKeys = contextRefresher.refresh();

        return ResponseEntity.ok(Map.of(
            "refreshedKeys", refreshedKeys,
            "currentConfig", Map.of(
                "environment", appConfig.getEnvironment(),
                "features", appConfig.getFeatures()
            )
        ));
    }
}
```

### RefreshScope for Dynamic Beans

```java
@Configuration
@RefreshScope
public class DynamicConfig {

    @Bean
    @RefreshScope
    public HttpClient httpClient(AppConfig config) {
        return HttpClient.newBuilder()
            .connectTimeout(config.getHttp().getConnectTimeout())
            .build();
    }
}
```

---

## Testing Configuration

### Unit Testing Configuration Classes

```java
class DatabaseConfigTest {

    @Test
    void getJdbcUrl_buildsCorrectUrl() {
        DatabaseConfig config = new DatabaseConfig();
        config.setHost("db.example.com");
        config.setPort(5432);
        config.setName("testdb");

        assertThat(config.getJdbcUrl())
            .isEqualTo("jdbc:postgresql://db.example.com:5432/testdb");
    }
}
```

### Integration Testing with Test Properties

```java
@SpringBootTest
@TestPropertySource(properties = {
    "app.environment=DEVELOPMENT",
    "app.database.host=test-db",
    "app.database.port=5433",
    "app.database.name=test_db",
    "security.jwt-secret=test-secret-key-for-testing-32chars"
})
class ConfigurationIntegrationTest {

    @Autowired AppConfig appConfig;
    @Autowired SecurityConfig securityConfig;

    @Test
    void configurationLoadsCorrectly() {
        assertThat(appConfig.getEnvironment()).isEqualTo(Environment.DEVELOPMENT);
        assertThat(appConfig.getDatabase().getHost()).isEqualTo("test-db");
        assertThat(appConfig.getDatabase().getPort()).isEqualTo(5433);
    }

    @Test
    void securityConfigLoadsCorrectly() {
        assertThat(securityConfig.getJwtSecret()).hasSize(32);
    }
}
```

### Testing with @ConfigurationPropertiesTest

```java
@ConfigurationPropertiesTest
@EnableConfigurationProperties(AppConfig.class)
@TestPropertySource(properties = {
    "app.name=test-app",
    "app.database.name=testdb"
})
class AppConfigPropertiesTest {

    @Autowired AppConfig config;

    @Test
    void bindsPropertiesCorrectly() {
        assertThat(config.getName()).isEqualTo("test-app");
        assertThat(config.getDatabase().getName()).isEqualTo("testdb");
    }

    @Test
    void validatesRequiredProperties() {
        // Missing required properties should fail validation
    }
}
```

### Mocking Configuration in Tests

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock AppConfig appConfig;
    @Mock HttpConfig httpConfig;

    @InjectMocks UserService userService;

    @BeforeEach
    void setUp() {
        when(appConfig.getHttp()).thenReturn(httpConfig);
        when(httpConfig.getConnectTimeout()).thenReturn(Duration.ofSeconds(5));
    }

    @Test
    void usesConfiguredTimeout() {
        // Test uses mocked configuration
    }
}
```

---

## Summary: Configuration Rules

| Rule | Description |
|------|-------------|
| **Type-Safe** | Use `@ConfigurationProperties` exclusively |
| **Validated** | Add `@Validated` and validation annotations |
| **Fail-Fast** | Validate at startup, not runtime |
| **Profiles** | Use profiles for environment-specific config |
| **Secrets** | Environment variables or vault, never in code |
| **Defaults** | Provide sensible defaults for development |
| **Documentation** | Javadoc on all configuration properties |
| **Testing** | Test configuration binding and validation |

---

**Related Files:**

* [SKILL.md](../SKILL.md) - Main skill guide
* [async-and-errors.md](async-and-errors.md) - Error handling patterns
* [testing-guide.md](testing-guide.md) - Testing patterns
