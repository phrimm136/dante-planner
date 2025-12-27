# Services and Repositories – Enterprise Business Logic Layer

Production-ready guide to organizing business logic with services and data access with repositories in Spring Boot.

## Table of Contents

* [Service Architecture Overview](#service-architecture-overview)
* [Command/Query Separation (CQRS-lite)](#commandquery-separation-cqrs-lite)
* [Domain Service Patterns](#domain-service-patterns)
* [Application Service Patterns](#application-service-patterns)
* [Repository Patterns (Spring Data JPA)](#repository-patterns-spring-data-jpa)
* [Event Publishing](#event-publishing)
* [Object Mapping (MapStruct)](#object-mapping-mapstruct)
* [Transaction Management](#transaction-management)
* [Caching Strategies](#caching-strategies)
* [Testing Services](#testing-services)

---

## Service Architecture Overview

### Layer Responsibilities

```
Controller     → HTTP mapping, request/response transformation
                      ↓
Application    → Use case orchestration, transactions, events
Service               ↓
Domain         → Core business rules, domain validation
Service               ↓
Repository     → Data access abstraction
```

### Service Types

| Type | Responsibility | Examples |
|------|----------------|----------|
| **Application Service** | Use case orchestration | `PostApplicationService` |
| **Domain Service** | Business rules spanning entities | `PostPermissionService` |
| **Infrastructure Service** | External system integration | `EmailService`, `StorageService` |

---

## Command/Query Separation (CQRS-lite)

### Why Separate Commands and Queries?

* **Commands** change state (write) → transactional, may publish events
* **Queries** read state (read) → optimized for performance, cacheable

### Command Service Pattern

```java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PostCommandService {

    private final PostRepository postRepository;
    private final PostPermissionService permissionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PostMapper postMapper;

    /**
     * Creates a new post with full validation and event publishing.
     *
     * @param command the creation command
     * @param userId the authenticated user ID
     * @return the created post DTO
     * @throws ForbiddenException if user lacks permission
     */
    public PostDto createPost(CreatePostCommand command, Long userId) {
        log.debug("Creating post for user: {}", userId);

        // Business rule validation
        permissionService.validateCanCreatePost(userId);

        // Domain object creation
        Post post = Post.builder()
            .title(command.title())
            .content(command.content())
            .authorId(userId)
            .status(PostStatus.DRAFT)
            .build();

        // Persist using custom repository method (not save())
        Post savedPost = postRepository.persist(post);

        // Publish domain event
        eventPublisher.publishEvent(new PostCreatedEvent(savedPost.getId(), userId));

        log.info("Post created: id={}, author={}", savedPost.getId(), userId);
        return postMapper.toDto(savedPost);
    }

    /**
     * Updates an existing post with optimistic locking.
     */
    public PostDto updatePost(Long postId, UpdatePostCommand command, Long userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post", postId));

        permissionService.validateCanEditPost(userId, post);

        // Domain method for state change
        post.updateContent(command.title(), command.content());

        // Explicit merge (not save)
        Post updatedPost = postRepository.update(post);

        eventPublisher.publishEvent(new PostUpdatedEvent(postId, userId));

        return postMapper.toDto(updatedPost);
    }

    /**
     * Soft deletes a post.
     */
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post", postId));

        permissionService.validateCanDeletePost(userId, post);

        // Soft delete via domain method
        post.markAsDeleted();

        eventPublisher.publishEvent(new PostDeletedEvent(postId, userId));

        log.info("Post soft-deleted: id={}, by={}", postId, userId);
    }
}
```

### Query Service Pattern

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;

    /**
     * Fetches a single post with author info (prevents N+1).
     */
    public PostDetailDto getPost(Long postId) {
        return postRepository.findWithAuthorById(postId)
            .map(postMapper::toDetailDto)
            .orElseThrow(() -> new NotFoundException("Post", postId));
    }

    /**
     * Fetches paginated posts using keyset pagination.
     */
    public CursorPage<PostSummaryDto> getPosts(PostSearchCriteria criteria) {
        Slice<Post> posts = postRepository.findBySearchCriteria(
            criteria.lastId(),
            criteria.lastCreatedAt(),
            criteria.status(),
            PageRequest.of(0, criteria.size())
        );

        List<PostSummaryDto> content = posts.getContent().stream()
            .map(postMapper::toSummaryDto)
            .toList();

        return CursorPage.of(content, posts.hasNext());
    }

    /**
     * Fetches posts by author with statistics (optimized query).
     */
    public AuthorPostsDto getPostsByAuthor(Long authorId) {
        List<PostWithStats> posts = postRepository.findWithStatsByAuthorId(authorId);
        return postMapper.toAuthorPostsDto(authorId, posts);
    }
}
```

---

## Domain Service Patterns

### Permission Service

```java
@Service
@RequiredArgsConstructor
public class PostPermissionService {

    private final UserRepository userRepository;

    /**
     * Validates user can create posts.
     *
     * @throws ForbiddenException if user lacks permission
     */
    public void validateCanCreatePost(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User", userId));

        if (!user.hasPermission(Permission.CREATE_POST)) {
            throw new ForbiddenException("User cannot create posts");
        }

        if (user.isPostLimitReached()) {
            throw new BusinessException("POST_LIMIT_REACHED",
                "You have reached the maximum number of posts");
        }
    }

    /**
     * Validates user can edit a specific post.
     */
    public void validateCanEditPost(Long userId, Post post) {
        if (!post.isAuthor(userId) && !hasAdminAccess(userId)) {
            throw new ForbiddenException("User cannot edit this post");
        }

        if (post.isLocked()) {
            throw new BusinessException("POST_LOCKED",
                "This post is locked for editing");
        }
    }

    /**
     * Validates user can delete a specific post.
     */
    public void validateCanDeletePost(Long userId, Post post) {
        if (!post.isAuthor(userId) && !hasAdminAccess(userId)) {
            throw new ForbiddenException("User cannot delete this post");
        }
    }

    private boolean hasAdminAccess(Long userId) {
        return userRepository.hasRole(userId, Role.ADMIN);
    }
}
```

### Calculation Service

```java
@Service
@RequiredArgsConstructor
public class PostStatisticsService {

    private final PostRepository postRepository;
    private final ViewRepository viewRepository;
    private final CommentRepository commentRepository;

    /**
     * Calculates engagement score for a post.
     * Formula: (views * 1) + (comments * 5) + (shares * 10)
     */
    public double calculateEngagementScore(Long postId) {
        long views = viewRepository.countByPostId(postId);
        long comments = commentRepository.countByPostId(postId);
        long shares = postRepository.getShareCount(postId);

        return (views * 1.0) + (comments * 5.0) + (shares * 10.0);
    }

    /**
     * Calculates trending score with time decay.
     */
    public double calculateTrendingScore(Long postId, LocalDateTime createdAt) {
        double engagement = calculateEngagementScore(postId);
        long hoursAge = ChronoUnit.HOURS.between(createdAt, LocalDateTime.now());

        // Gravity factor for time decay
        double gravity = 1.8;
        return engagement / Math.pow(hoursAge + 2, gravity);
    }
}
```

---

## Application Service Patterns

### Orchestrating Multiple Services

```java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PostPublishingService {

    private final PostRepository postRepository;
    private final PostPermissionService permissionService;
    private final NotificationService notificationService;
    private final SearchIndexService searchIndexService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publishes a draft post with full workflow.
     */
    public PostDto publishPost(Long postId, Long userId) {
        log.info("Publishing post: id={}, user={}", postId, userId);

        // 1. Load and validate
        Post post = postRepository.findWithAuthorById(postId)
            .orElseThrow(() -> new NotFoundException("Post", postId));

        permissionService.validateCanEditPost(userId, post);

        if (!post.isDraft()) {
            throw new BusinessException("INVALID_STATE",
                "Only draft posts can be published");
        }

        // 2. Domain state change
        post.publish();

        // 3. Persist
        Post publishedPost = postRepository.update(post);

        // 4. Side effects (async via events)
        eventPublisher.publishEvent(new PostPublishedEvent(postId, post.getAuthorId()));

        log.info("Post published successfully: id={}", postId);
        return postMapper.toDto(publishedPost);
    }
}

// Event listener for async side effects
@Component
@RequiredArgsConstructor
@Slf4j
public class PostPublishedEventHandler {

    private final NotificationService notificationService;
    private final SearchIndexService searchIndexService;

    @Async
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostPublished(PostPublishedEvent event) {
        log.debug("Handling PostPublishedEvent: postId={}", event.postId());

        // Notify followers
        notificationService.notifyFollowers(event.authorId(), event.postId());

        // Update search index
        searchIndexService.indexPost(event.postId());
    }
}
```

---

## Repository Patterns (Spring Data JPA)

### Custom Base Repository

See [database-patterns.md](database-patterns.md) for the `BaseRepository` implementation.

```java
public interface PostRepository extends BaseRepository<Post, Long> {

    // Derived queries
    List<Post> findByAuthorIdAndStatusOrderByCreatedAtDesc(Long authorId, PostStatus status);

    boolean existsByTitleAndAuthorId(String title, Long authorId);

    // Fetch join to prevent N+1
    @Query("""
        SELECT p FROM Post p
        JOIN FETCH p.author
        LEFT JOIN FETCH p.tags
        WHERE p.id = :id
        AND p.deleted = false
        """)
    Optional<Post> findWithAuthorById(@Param("id") Long id);

    // Keyset pagination (cursor-based)
    @Query("""
        SELECT p FROM Post p
        WHERE (:lastId IS NULL OR p.id < :lastId)
        AND (:lastCreatedAt IS NULL OR p.createdAt <= :lastCreatedAt)
        AND p.status = :status
        AND p.deleted = false
        ORDER BY p.createdAt DESC, p.id DESC
        """)
    Slice<Post> findBySearchCriteria(
        @Param("lastId") Long lastId,
        @Param("lastCreatedAt") LocalDateTime lastCreatedAt,
        @Param("status") PostStatus status,
        Pageable pageable
    );

    // DTO projection for read-optimized queries
    @Query("""
        SELECT new com.example.dto.PostWithStats(
            p.id, p.title, p.createdAt,
            COUNT(DISTINCT c.id),
            COUNT(DISTINCT v.id)
        )
        FROM Post p
        LEFT JOIN Comment c ON c.postId = p.id
        LEFT JOIN View v ON v.postId = p.id
        WHERE p.authorId = :authorId
        AND p.deleted = false
        GROUP BY p.id, p.title, p.createdAt
        ORDER BY p.createdAt DESC
        """)
    List<PostWithStats> findWithStatsByAuthorId(@Param("authorId") Long authorId);
}
```

### Specification Pattern for Complex Queries

```java
@RequiredArgsConstructor
public class PostSpecifications {

    public static Specification<Post> withCriteria(PostSearchCriteria criteria) {
        return Specification
            .where(notDeleted())
            .and(hasStatus(criteria.status()))
            .and(hasAuthor(criteria.authorId()))
            .and(containsKeyword(criteria.keyword()))
            .and(createdAfter(criteria.startDate()))
            .and(createdBefore(criteria.endDate()));
    }

    private static Specification<Post> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    private static Specification<Post> hasStatus(PostStatus status) {
        return status == null ? null :
            (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Post> hasAuthor(Long authorId) {
        return authorId == null ? null :
            (root, query, cb) -> cb.equal(root.get("authorId"), authorId);
    }

    private static Specification<Post> containsKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"),
            cb.like(cb.lower(root.get("content")), "%" + keyword.toLowerCase() + "%")
        );
    }

    private static Specification<Post> createdAfter(LocalDateTime date) {
        return date == null ? null :
            (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), date);
    }

    private static Specification<Post> createdBefore(LocalDateTime date) {
        return date == null ? null :
            (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), date);
    }
}

// Usage in service
public Page<PostDto> searchPosts(PostSearchCriteria criteria, Pageable pageable) {
    return postRepository.findAll(PostSpecifications.withCriteria(criteria), pageable)
        .map(postMapper::toDto);
}
```

---

## Event Publishing

### Domain Events

```java
// Event definitions
public sealed interface PostEvent permits PostCreatedEvent, PostUpdatedEvent, PostDeletedEvent, PostPublishedEvent {
    Long postId();
    Instant occurredAt();
}

public record PostCreatedEvent(
    Long postId,
    Long authorId,
    Instant occurredAt
) implements PostEvent {
    public PostCreatedEvent(Long postId, Long authorId) {
        this(postId, authorId, Instant.now());
    }
}

public record PostPublishedEvent(
    Long postId,
    Long authorId,
    Instant occurredAt
) implements PostEvent {
    public PostPublishedEvent(Long postId, Long authorId) {
        this(postId, authorId, Instant.now());
    }
}
```

### Event Listeners

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventListeners {

    private final AuditLogService auditLogService;
    private final AnalyticsService analyticsService;

    /**
     * Synchronous listener - runs in same transaction.
     */
    @EventListener
    public void onPostCreated(PostCreatedEvent event) {
        log.debug("Post created event: {}", event.postId());
        auditLogService.log(AuditAction.POST_CREATED, event.postId(), event.authorId());
    }

    /**
     * Async listener - runs after transaction commits.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        log.debug("Post published event (async): {}", event.postId());
        analyticsService.trackPublish(event.postId());
    }
}
```

---

## Object Mapping (MapStruct)

### Mapper Definition

```java
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PostMapper {

    @Mapping(target = "authorName", source = "author.name")
    @Mapping(target = "tagNames", source = "tags", qualifiedByName = "tagsToNames")
    PostDto toDto(Post post);

    @Mapping(target = "commentCount", source = "commentCount")
    @Mapping(target = "viewCount", source = "viewCount")
    PostDetailDto toDetailDto(Post post);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Post toEntity(CreatePostCommand command);

    @Named("tagsToNames")
    default List<String> tagsToNames(Set<Tag> tags) {
        if (tags == null) return List.of();
        return tags.stream()
            .map(Tag::getName)
            .sorted()
            .toList();
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Post post, UpdatePostCommand command);
}
```

### DTO Definitions

```java
// Response DTOs (immutable)
public record PostDto(
    Long id,
    String title,
    String content,
    String authorName,
    List<String> tagNames,
    PostStatus status,
    LocalDateTime createdAt
) {}

public record PostDetailDto(
    Long id,
    String title,
    String content,
    AuthorDto author,
    List<String> tagNames,
    PostStatus status,
    long commentCount,
    long viewCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

public record PostSummaryDto(
    Long id,
    String title,
    String authorName,
    LocalDateTime createdAt
) {}

// Command DTOs (validated)
public record CreatePostCommand(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 50000) String content,
    @Size(max = 10) Set<@NotBlank String> tags
) {}

public record UpdatePostCommand(
    @Size(max = 200) String title,
    @Size(max = 50000) String content,
    Set<@NotBlank String> tags
) {}
```

---

## Transaction Management

### Transaction Boundaries

```java
@Service
@RequiredArgsConstructor
public class PostTransactionService {

    private final PostRepository postRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Standard transactional method.
     */
    @Transactional
    public PostDto createPost(CreatePostCommand command) {
        // Entire method is transactional
        Post post = Post.create(command);
        return postMapper.toDto(postRepository.persist(post));
    }

    /**
     * Read-only transaction (optimized).
     */
    @Transactional(readOnly = true)
    public PostDto getPost(Long id) {
        // No dirty checking, no flush
        return postRepository.findById(id)
            .map(postMapper::toDto)
            .orElseThrow(() -> new NotFoundException("Post", id));
    }

    /**
     * Programmatic transaction control.
     */
    public void processPostsInBatches(List<Long> postIds) {
        Lists.partition(postIds, 100).forEach(batch -> {
            transactionTemplate.executeWithoutResult(status -> {
                batch.forEach(this::processPost);
            });
        });
    }

    /**
     * New transaction for independent operation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPostAccess(Long postId, Long userId) {
        // Independent transaction - won't rollback parent
        accessLogRepository.save(AccessLog.create(postId, userId));
    }
}
```

### Optimistic Locking Handling

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PostUpdateService {

    private final PostRepository postRepository;
    private static final int MAX_RETRIES = 3;

    /**
     * Updates post with optimistic lock retry.
     */
    @Transactional
    public PostDto updateWithRetry(Long postId, UpdatePostCommand command) {
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            try {
                return doUpdate(postId, command);
            } catch (OptimisticLockingFailureException e) {
                attempts++;
                log.warn("Optimistic lock conflict on post {}, attempt {}/{}",
                    postId, attempts, MAX_RETRIES);

                if (attempts >= MAX_RETRIES) {
                    throw new ConflictException("Post was modified by another user. Please refresh and try again.");
                }

                // Small delay before retry
                try {
                    Thread.sleep(100 * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("Operation interrupted");
                }
            }
        }

        throw new ServiceException("Failed to update post after " + MAX_RETRIES + " attempts");
    }

    private PostDto doUpdate(Long postId, UpdatePostCommand command) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post", postId));

        post.updateContent(command.title(), command.content());

        return postMapper.toDto(postRepository.update(post));
    }
}
```

---

## Caching Strategies

### Spring Cache Abstraction

```java
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "posts")
public class CachedPostQueryService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;

    /**
     * Cached single post lookup.
     */
    @Cacheable(key = "#postId", unless = "#result == null")
    @Transactional(readOnly = true)
    public PostDto getPost(Long postId) {
        return postRepository.findById(postId)
            .map(postMapper::toDto)
            .orElse(null);
    }

    /**
     * Evict cache on update.
     */
    @CacheEvict(key = "#postId")
    @Transactional
    public PostDto updatePost(Long postId, UpdatePostCommand command) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post", postId));

        post.updateContent(command.title(), command.content());

        return postMapper.toDto(postRepository.update(post));
    }

    /**
     * Update cache after modification.
     */
    @CachePut(key = "#postId")
    @Transactional
    public PostDto publishPost(Long postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post", postId));

        post.publish();

        return postMapper.toDto(postRepository.update(post));
    }
}
```

### Cache Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats());
        return cacheManager;
    }
}
```

---

## Testing Services

### Unit Test with Mocks

```java
@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    @Mock PostRepository postRepository;
    @Mock PostPermissionService permissionService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PostMapper postMapper;

    @InjectMocks PostCommandService service;

    @Test
    void createPost_withValidInput_persistsAndPublishesEvent() {
        // Given
        Long userId = 1L;
        CreatePostCommand command = new CreatePostCommand("Title", "Content", Set.of());
        Post post = Post.builder().id(1L).title("Title").build();
        PostDto expectedDto = new PostDto(1L, "Title", "Content", "Author", List.of(), PostStatus.DRAFT, LocalDateTime.now());

        when(postRepository.persist(any(Post.class))).thenReturn(post);
        when(postMapper.toDto(post)).thenReturn(expectedDto);

        // When
        PostDto result = service.createPost(command, userId);

        // Then
        assertThat(result.id()).isEqualTo(1L);
        verify(permissionService).validateCanCreatePost(userId);
        verify(postRepository).persist(any(Post.class));
        verify(eventPublisher).publishEvent(any(PostCreatedEvent.class));
    }

    @Test
    void createPost_withoutPermission_throwsForbiddenException() {
        // Given
        Long userId = 1L;
        CreatePostCommand command = new CreatePostCommand("Title", "Content", Set.of());

        doThrow(new ForbiddenException("No permission"))
            .when(permissionService).validateCanCreatePost(userId);

        // When & Then
        assertThatThrownBy(() -> service.createPost(command, userId))
            .isInstanceOf(ForbiddenException.class);

        verify(postRepository, never()).persist(any());
    }
}
```

### Integration Test

```java
@SpringBootTest
@Transactional
class PostCommandServiceIntegrationTest {

    @Autowired PostCommandService service;
    @Autowired PostRepository postRepository;
    @Autowired UserRepository userRepository;

    @Test
    void createPost_persistsToDatabase() {
        // Given
        User user = userRepository.persist(User.builder()
            .name("Test User")
            .email("test@example.com")
            .build());

        CreatePostCommand command = new CreatePostCommand("Title", "Content", Set.of("tag1"));

        // When
        PostDto result = service.createPost(command, user.getId());

        // Then
        assertThat(result.id()).isNotNull();

        Post persisted = postRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Title");
        assertThat(persisted.getStatus()).isEqualTo(PostStatus.DRAFT);
    }
}
```

---

## Summary: Service Layer Rules

| Rule | Description |
|------|-------------|
| **CQRS-lite** | Separate command (write) and query (read) services |
| **Domain Services** | Encapsulate business rules spanning entities |
| **Event Publishing** | Use events for side effects and async operations |
| **MapStruct** | Type-safe object mapping with compile-time validation |
| **Transaction Boundaries** | `@Transactional` on service methods, `readOnly=true` for queries |
| **Optimistic Locking** | Handle concurrent modifications with retry logic |
| **Caching** | Use Spring Cache for frequently accessed data |

---

**Related Files:**

* [SKILL.md](../SKILL.md) - Main skill guide
* [routing-and-controllers.md](routing-and-controllers.md) - Controller patterns
* [database-patterns.md](database-patterns.md) - Repository and JPA patterns
* [async-and-errors.md](async-and-errors.md) - Async and exception handling
