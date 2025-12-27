# Routing and Controllers – Enterprise Spring Boot Patterns

Production-ready guide to clean request mapping, controller design, and API best practices in Spring Boot.

## Table of Contents

* [Controller Architecture](#controller-architecture)
* [Request Mapping Patterns](#request-mapping-patterns)
* [Request/Response DTOs](#requestresponse-dtos)
* [API Versioning](#api-versioning)
* [Authentication Context](#authentication-context)
* [Global Exception Handling](#global-exception-handling)
* [Response Envelope Pattern](#response-envelope-pattern)
* [OpenAPI Documentation](#openapi-documentation)
* [Rate Limiting](#rate-limiting)
* [Testing Controllers](#testing-controllers)

---

## Controller Architecture

### The Golden Rule

**Controllers are THIN delegation layers.** They handle:

* HTTP request/response mapping
* Input validation triggering (`@Valid`)
* Authentication context extraction
* Response status codes
* Delegation to services

**Controllers NEVER contain:**

* Business logic
* Repository access
* Complex conditional logic
* Transaction management

### Clean Controller Pattern

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Post management endpoints")
@Slf4j
public class PostController {

    private final PostCommandService commandService;
    private final PostQueryService queryService;

    @GetMapping("/{id}")
    @Operation(summary = "Get post by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post found"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public ResponseEntity<ApiResponse<PostDetailDto>> getPost(
            @PathVariable @Positive Long id) {

        PostDetailDto post = queryService.getPost(id);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create new post")
    public ResponseEntity<ApiResponse<PostDto>> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        PostDto post = commandService.createPost(request.toCommand(), principal.getId());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(post.id())
            .toUri();

        return ResponseEntity.created(location)
            .body(ApiResponse.success(post));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update existing post")
    public ResponseEntity<ApiResponse<PostDto>> updatePost(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        PostDto post = commandService.updatePost(id, request.toCommand(), principal.getId());
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete post")
    public ResponseEntity<Void> deletePost(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        commandService.deletePost(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
```

---

## Request Mapping Patterns

### RESTful Resource Naming

```java
// Resource collection
@GetMapping                           // GET /api/v1/posts
@PostMapping                          // POST /api/v1/posts

// Single resource
@GetMapping("/{id}")                  // GET /api/v1/posts/123
@PutMapping("/{id}")                  // PUT /api/v1/posts/123
@PatchMapping("/{id}")                // PATCH /api/v1/posts/123
@DeleteMapping("/{id}")               // DELETE /api/v1/posts/123

// Sub-resources
@GetMapping("/{postId}/comments")     // GET /api/v1/posts/123/comments
@PostMapping("/{postId}/comments")    // POST /api/v1/posts/123/comments

// Actions (when REST doesn't fit)
@PostMapping("/{id}/publish")         // POST /api/v1/posts/123/publish
@PostMapping("/{id}/archive")         // POST /api/v1/posts/123/archive
```

### Query Parameters for Filtering

```java
@GetMapping
public ResponseEntity<ApiResponse<CursorPage<PostSummaryDto>>> getPosts(
        @RequestParam(required = false) PostStatus status,
        @RequestParam(required = false) Long authorId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long cursor,
        @RequestParam(defaultValue = "20") @Max(100) int size) {

    PostSearchCriteria criteria = PostSearchCriteria.builder()
        .status(status)
        .authorId(authorId)
        .keyword(keyword)
        .lastId(cursor)
        .size(size)
        .build();

    CursorPage<PostSummaryDto> posts = queryService.getPosts(criteria);
    return ResponseEntity.ok(ApiResponse.success(posts));
}
```

### Path Variable Validation

```java
@GetMapping("/{id}")
public ResponseEntity<PostDto> getPost(
        @PathVariable @Positive(message = "ID must be positive") Long id) {
    return ResponseEntity.ok(queryService.getPost(id));
}

@GetMapping("/slug/{slug}")
public ResponseEntity<PostDto> getPostBySlug(
        @PathVariable @Pattern(regexp = "^[a-z0-9-]+$") String slug) {
    return ResponseEntity.ok(queryService.getPostBySlug(slug));
}
```

---

## Request/Response DTOs

### Request DTOs (Commands)

```java
/**
 * Request DTO for creating a post.
 * Converts to domain command object.
 */
public record CreatePostRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    String title,

    @NotBlank(message = "Content is required")
    @Size(max = 50000, message = "Content must be at most 50000 characters")
    String content,

    @Size(max = 10, message = "Maximum 10 tags allowed")
    Set<@NotBlank @Size(max = 30) String> tags
) {
    /**
     * Converts to domain command.
     */
    public CreatePostCommand toCommand() {
        return new CreatePostCommand(title, content, tags != null ? tags : Set.of());
    }
}

/**
 * Request DTO for updating a post.
 * All fields optional for partial updates.
 */
public record UpdatePostRequest(
    @Size(max = 200, message = "Title must be at most 200 characters")
    String title,

    @Size(max = 50000, message = "Content must be at most 50000 characters")
    String content,

    @Size(max = 10, message = "Maximum 10 tags allowed")
    Set<@NotBlank @Size(max = 30) String> tags
) {
    public UpdatePostCommand toCommand() {
        return new UpdatePostCommand(title, content, tags);
    }
}
```

### Response DTOs

```java
/**
 * Summary DTO for list views.
 */
public record PostSummaryDto(
    Long id,
    String title,
    String authorName,
    PostStatus status,
    LocalDateTime createdAt
) {}

/**
 * Detail DTO with full information.
 */
public record PostDetailDto(
    Long id,
    String title,
    String content,
    AuthorDto author,
    List<String> tags,
    PostStatus status,
    long commentCount,
    long viewCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

/**
 * Cursor-based pagination response.
 */
public record CursorPage<T>(
    List<T> content,
    Long nextCursor,
    boolean hasNext
) {
    public static <T> CursorPage<T> of(List<T> content, boolean hasNext) {
        Long nextCursor = hasNext && !content.isEmpty()
            ? extractId(content.get(content.size() - 1))
            : null;
        return new CursorPage<>(content, nextCursor, hasNext);
    }
}
```

---

## API Versioning

### URL Path Versioning (Recommended)

```java
@RestController
@RequestMapping("/api/v1/posts")
public class PostControllerV1 {
    // Version 1 implementation
}

@RestController
@RequestMapping("/api/v2/posts")
public class PostControllerV2 {
    // Version 2 with breaking changes
}
```

### Header-Based Versioning (Alternative)

```java
@RestController
@RequestMapping("/api/posts")
public class PostController {

    @GetMapping(headers = "X-API-Version=1")
    public ResponseEntity<PostDtoV1> getPostV1(@PathVariable Long id) {
        // V1 response
    }

    @GetMapping(headers = "X-API-Version=2")
    public ResponseEntity<PostDtoV2> getPostV2(@PathVariable Long id) {
        // V2 response with additional fields
    }
}
```

### Version Configuration

```java
@Configuration
public class ApiVersionConfig {

    public static final String CURRENT_VERSION = "v1";
    public static final String API_BASE_PATH = "/api/" + CURRENT_VERSION;
}
```

---

## Authentication Context

### Security Principal

```java
/**
 * Custom user principal with application-specific data.
 */
@Getter
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String name;
    private final Set<Role> roles;
    private final boolean enabled;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return email;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
```

### Using in Controllers

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService queryService;

    /**
     * Get current user profile.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {

        UserProfileDto profile = queryService.getUserProfile(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    /**
     * Admin-only endpoint.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        List<UserDto> users = queryService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Owner or admin access.
     */
    @GetMapping("/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId, authentication)")
    public ResponseEntity<ApiResponse<UserDto>> getUser(
            @PathVariable Long userId) {

        UserDto user = queryService.getUser(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
```

### Security Service for Complex Authorization

```java
@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    public boolean canAccessUser(Long userId, Authentication authentication) {
        if (authentication == null) return false;

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return principal.getId().equals(userId) || principal.isAdmin();
    }

    public boolean canEditPost(Long postId, Authentication authentication) {
        if (authentication == null) return false;

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (principal.isAdmin()) return true;

        return postRepository.isAuthor(postId, principal.getId());
    }
}
```

---

## Global Exception Handling

### Exception Hierarchy

```java
/**
 * Base application exception.
 */
@Getter
public abstract class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final Map<String, Object> details;

    protected ApplicationException(String errorCode, String message, HttpStatus status) {
        this(errorCode, message, status, Map.of());
    }

    protected ApplicationException(String errorCode, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.details = details;
    }
}

public class NotFoundException extends ApplicationException {
    public NotFoundException(String resourceType, Object resourceId) {
        super(
            "RESOURCE_NOT_FOUND",
            String.format("%s with id '%s' not found", resourceType, resourceId),
            HttpStatus.NOT_FOUND,
            Map.of("resourceType", resourceType, "resourceId", resourceId)
        );
    }
}

public class ForbiddenException extends ApplicationException {
    public ForbiddenException(String message) {
        super("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }
}

public class ConflictException extends ApplicationException {
    public ConflictException(String message) {
        super("CONFLICT", message, HttpStatus.CONFLICT);
    }
}

public class BusinessException extends ApplicationException {
    public BusinessException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    /**
     * Handle application-specific exceptions.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(
            ApplicationException ex, WebRequest request) {

        log.warn("Application exception: {} - {}", ex.getErrorCode(), ex.getMessage());

        return ResponseEntity.status(ex.getStatus())
            .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
    }

    /**
     * Handle validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                (existing, replacement) -> existing
            ));

        log.debug("Validation failed: {}", fieldErrors);

        return ResponseEntity.badRequest()
            .body(ApiResponse.validationError(fieldErrors));
    }

    /**
     * Handle constraint violations (path/query params).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> extractPropertyName(v.getPropertyPath()),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing
            ));

        return ResponseEntity.badRequest()
            .body(ApiResponse.validationError(errors));
    }

    /**
     * Handle JSON parse errors.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {

        log.debug("Message not readable: {}", ex.getMessage());

        return ResponseEntity.badRequest()
            .body(ApiResponse.error("INVALID_JSON", "Invalid request body format"));
    }

    /**
     * Handle method not allowed.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex) {

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.error("METHOD_NOT_ALLOWED",
                "Method " + ex.getMethod() + " not supported"));
    }

    /**
     * Handle optimistic locking failures.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockingFailureException ex) {

        log.warn("Optimistic lock failure: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("CONCURRENT_MODIFICATION",
                "Resource was modified by another user. Please refresh and try again."));
    }

    /**
     * Handle all unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception ex, WebRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected error [traceId={}]: {}", traceId, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR",
                "An unexpected error occurred. Reference: " + traceId));
    }

    private String extractPropertyName(Path propertyPath) {
        String fullPath = propertyPath.toString();
        int lastDot = fullPath.lastIndexOf('.');
        return lastDot >= 0 ? fullPath.substring(lastDot + 1) : fullPath;
    }
}
```

---

## Response Envelope Pattern

### Standard API Response

```java
/**
 * Standard API response envelope.
 *
 * @param <T> the type of response data
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorInfo error;
    private final Instant timestamp;

    private ApiResponse(boolean success, T data, ErrorInfo error) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorInfo(code, message, null));
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(false, null, new ErrorInfo(code, message, details));
    }

    public static ApiResponse<Void> validationError(Map<String, String> fieldErrors) {
        return new ApiResponse<>(false, null,
            new ErrorInfo("VALIDATION_ERROR", "Validation failed", Map.of("fields", fieldErrors)));
    }

    @Getter
    @RequiredArgsConstructor
    public static class ErrorInfo {
        private final String code;
        private final String message;
        private final Map<String, Object> details;
    }
}
```

### Response Examples

```json
// Success response
{
  "success": true,
  "data": {
    "id": 123,
    "title": "My Post",
    "content": "Content here..."
  },
  "timestamp": "2024-01-15T10:30:00Z"
}

// Error response
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Post with id '999' not found",
    "details": {
      "resourceType": "Post",
      "resourceId": 999
    }
  },
  "timestamp": "2024-01-15T10:30:00Z"
}

// Validation error response
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": {
      "fields": {
        "title": "Title is required",
        "content": "Content must be at most 50000 characters"
      }
    }
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## OpenAPI Documentation

### Controller Documentation

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Post management API")
public class PostController {

    @Operation(
        summary = "Create a new post",
        description = "Creates a new post for the authenticated user"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Post created successfully",
            content = @Content(schema = @Schema(implementation = PostDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request body"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication required"
        )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PostDto>> createPost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Post creation data",
                required = true
            )
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Implementation
    }
}
```

### OpenAPI Configuration

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Application API")
                .description("REST API documentation")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Development Team")
                    .email("dev@example.com")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
```

---

## Rate Limiting

### Bucket4j Integration

```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final RateLimiterService rateLimiter;

    @PostMapping
    public ResponseEntity<ApiResponse<PostDto>> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        // Rate limit by user
        rateLimiter.checkLimit("post:create:" + principal.getId(), 10, Duration.ofMinutes(1));

        PostDto post = commandService.createPost(request.toCommand(), principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(post));
    }
}
```

### Rate Limiter Service

```java
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void checkLimit(String key, long tokens, Duration duration) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(tokens, duration));

        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(
                "Rate limit exceeded. Try again later.",
                bucket.getAvailableTokens()
            );
        }
    }

    private Bucket createBucket(long tokens, Duration duration) {
        Bandwidth limit = Bandwidth.classic(tokens, Refill.intervally(tokens, duration));
        return Bucket.builder().addLimit(limit).build();
    }
}

public class RateLimitExceededException extends ApplicationException {
    public RateLimitExceededException(String message, long retryAfterMs) {
        super("RATE_LIMIT_EXCEEDED", message, HttpStatus.TOO_MANY_REQUESTS,
            Map.of("retryAfterMs", retryAfterMs));
    }
}
```

---

## Testing Controllers

### Unit Test with MockMvc

```java
@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PostCommandService commandService;
    @MockBean PostQueryService queryService;

    @Test
    @WithMockUser
    void createPost_withValidRequest_returnsCreated() throws Exception {
        // Given
        CreatePostRequest request = new CreatePostRequest("Title", "Content", Set.of());
        PostDto responseDto = new PostDto(1L, "Title", "Content", "Author",
            List.of(), PostStatus.DRAFT, LocalDateTime.now());

        when(commandService.createPost(any(), any())).thenReturn(responseDto);

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.title").value("Title"));
    }

    @Test
    @WithMockUser
    void createPost_withInvalidRequest_returnsBadRequest() throws Exception {
        // Given
        CreatePostRequest request = new CreatePostRequest("", "", null);

        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser
    void getPost_whenNotFound_returns404() throws Exception {
        // Given
        when(queryService.getPost(999L))
            .thenThrow(new NotFoundException("Post", 999L));

        // When & Then
        mockMvc.perform(get("/api/v1/posts/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getPost_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/posts/1"))
            .andExpect(status().isUnauthorized());
    }
}
```

### Integration Test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
class PostControllerIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired PostRepository postRepository;
    @Autowired UserRepository userRepository;

    @Test
    void fullPostLifecycle() {
        // Create user and get token
        String token = createUserAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // Create post
        CreatePostRequest createRequest = new CreatePostRequest("Title", "Content", Set.of());
        ResponseEntity<ApiResponse<PostDto>> createResponse = restTemplate.exchange(
            "/api/v1/posts",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, headers),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long postId = createResponse.getBody().getData().id();

        // Get post
        ResponseEntity<ApiResponse<PostDetailDto>> getResponse = restTemplate.exchange(
            "/api/v1/posts/" + postId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getData().title()).isEqualTo("Title");

        // Delete post
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            "/api/v1/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
```

---

## Summary: Controller Rules

| Rule | Description |
|------|-------------|
| **Thin Controllers** | Delegate all logic to services |
| **Validation at Boundary** | Use `@Valid` on request DTOs |
| **Standard Response Envelope** | Use `ApiResponse<T>` for consistency |
| **Global Error Handling** | `@RestControllerAdvice` for centralized error handling |
| **Proper Status Codes** | 201 for create, 204 for delete, etc. |
| **API Versioning** | URL path versioning (`/api/v1/...`) |
| **OpenAPI Documentation** | Document all endpoints with annotations |
| **Security Annotations** | Use `@PreAuthorize` for authorization |

---

**Related Files:**

* [SKILL.md](../SKILL.md) - Main skill guide
* [services-and-repositories.md](services-and-repositories.md) - Service layer patterns
* [validation-patterns.md](validation-patterns.md) - Validation patterns
* [async-and-errors.md](async-and-errors.md) - Exception handling details
