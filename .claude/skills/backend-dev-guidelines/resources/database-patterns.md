# Database Patterns - Enterprise Spring Data JPA

Production-ready database patterns using Spring Data JPA and Hibernate.

---

## Table of Contents

- [BaseEntity Pattern](#baseentity-pattern)
- [Entity Design](#entity-design)
- [Soft Delete Pattern](#soft-delete-pattern)
- [Repository Patterns](#repository-patterns)
- [Custom Repository Base Class](#custom-repository-base-class)
- [Transaction Management](#transaction-management)
- [Query Optimization](#query-optimization)
- [N+1 Prevention](#n1-prevention)
- [Batch Operations](#batch-operations)
- [Error Handling](#error-handling)

---

## BaseEntity Pattern

All entities extend a common base class for consistency and auditing.

### Abstract Base Entity

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    private Long version;  // Optimistic locking
}
```

### JPA Auditing Configuration

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .or(() -> Optional.of("system"));
    }
}
```

---

## Entity Design

### Domain Entity Example

```java
@Entity
@Table(name = "posts",
    indexes = {
        @Index(name = "idx_post_user_id", columnList = "user_id"),
        @Index(name = "idx_post_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_slug", columnNames = "slug")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Lob
    @Basic(fetch = FetchType.LAZY)  // Lazy load large content
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_post_user"))
    private User user;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)  // Prevent N+1 with batch fetching
    private List<Comment> comments = new ArrayList<>();

    // Factory method - enforces invariants
    public static Post create(String title, String content, User user) {
        Post post = new Post();
        post.title = title;
        post.slug = generateSlug(title);
        post.content = content;
        post.user = user;
        return post;
    }

    // Business methods - encapsulate state changes
    public void publish() {
        if (this.status == PostStatus.DELETED) {
            throw new IllegalStateException("Cannot publish deleted post");
        }
        this.status = PostStatus.PUBLISHED;
    }

    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setPost(this);
    }

    private static String generateSlug(String title) {
        return title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }
}
```

### Enum with Database Value

```java
public enum PostStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    DELETED("deleted");

    private final String value;

    PostStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
```

---

## Soft Delete Pattern

### Soft Delete Base Entity

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Where(clause = "deleted_at IS NULL")  // Global filter
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(String deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
```

### Entity with Soft Delete

```java
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ? AND version = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends SoftDeletableEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    // Calling delete() triggers soft delete via @SQLDelete
}
```

### Repository with Soft Delete Support

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Automatically excludes soft-deleted due to @Where
    Optional<User> findByEmail(String email);

    // Explicitly include soft-deleted records
    @Query("SELECT u FROM User u WHERE u.email = :email")
    @FilterIgnore  // Custom annotation to bypass @Where
    Optional<User> findByEmailIncludingDeleted(@Param("email") String email);

    // Find only deleted records
    @Query(value = "SELECT * FROM users WHERE email = :email AND deleted_at IS NOT NULL",
           nativeQuery = true)
    Optional<User> findDeletedByEmail(@Param("email") String email);
}
```

---

## Repository Patterns

### Standard Repository

```java
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // Derived queries
    List<Post> findByStatusOrderByCreatedAtDesc(PostStatus status);

    Optional<Post> findBySlug(String slug);

    boolean existsBySlug(String slug);

    // JPQL with JOIN FETCH (prevents N+1)
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id = :id")
    Optional<Post> findByIdWithUser(@Param("id") Long id);

    // Entity Graph (alternative to JOIN FETCH)
    @EntityGraph(attributePaths = {"user", "comments"})
    @Query("SELECT p FROM Post p WHERE p.slug = :slug")
    Optional<Post> findBySlugWithDetails(@Param("slug") String slug);

    // Modifying query
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.status = :status WHERE p.user.id = :userId")
    int updateStatusByUserId(@Param("userId") Long userId, @Param("status") PostStatus status);

    // Pagination
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    Slice<Post> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
```

### Projection Interface (Read-Only DTOs)

```java
// Interface projection - immutable, efficient
public interface PostSummary {
    Long getId();
    String getTitle();
    String getSlug();
    LocalDateTime getCreatedAt();

    @Value("#{target.user.name}")  // Nested property
    String getAuthorName();
}

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<PostSummary> findByStatusOrderByCreatedAtDesc(PostStatus status);

    @Query("""
        SELECT p.id as id, p.title as title, p.slug as slug,
               p.createdAt as createdAt, u.name as authorName
        FROM Post p JOIN p.user u
        WHERE p.status = :status
        """)
    List<PostSummary> findPublishedSummaries(@Param("status") PostStatus status);
}
```

### Class-Based Projection (DTO Constructor)

```java
public record PostDto(
    Long id,
    String title,
    String authorName,
    LocalDateTime createdAt
) {}

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        SELECT new com.example.dto.PostDto(p.id, p.title, u.name, p.createdAt)
        FROM Post p JOIN p.user u
        WHERE p.status = 'PUBLISHED'
        ORDER BY p.createdAt DESC
        """)
    List<PostDto> findPublishedPosts();
}
```

---

## Custom Repository Base Class

### Why Override Default Repository?

The default `save()` method is problematic:
- Can't determine if entity is new (triggers unnecessary SELECT)
- Uses `merge()` for all cases (inefficient for new entities)
- Poor performance in batch operations

### Custom Repository Interface

```java
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    /**
     * Persist a new entity. Use for new entities only.
     */
    <S extends T> S persist(S entity);

    /**
     * Persist multiple new entities with batch optimization.
     */
    <S extends T> List<S> persistAll(Iterable<S> entities);

    /**
     * Update a detached entity. Forces UPDATE without SELECT.
     */
    <S extends T> S update(S entity);

    /**
     * Update multiple detached entities with batch optimization.
     */
    <S extends T> List<S> updateAll(Iterable<S> entities);
}
```

### Custom Repository Implementation

```java
public class BaseRepositoryImpl<T, ID extends Serializable>
    extends SimpleJpaRepository<T, ID>
    implements BaseRepository<T, ID> {

    private final EntityManager entityManager;

    public BaseRepositoryImpl(JpaEntityInformation<T, ?> entityInformation,
                              EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public <S extends T> S persist(S entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    @Transactional
    public <S extends T> List<S> persistAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        int i = 0;
        for (S entity : entities) {
            entityManager.persist(entity);
            result.add(entity);

            // Batch flush every 50 entities
            if (++i % 50 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        return result;
    }

    @Override
    @Transactional
    public <S extends T> S update(S entity) {
        return entityManager.merge(entity);
    }

    @Override
    @Transactional
    public <S extends T> List<S> updateAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        int i = 0;
        for (S entity : entities) {
            result.add(entityManager.merge(entity));

            if (++i % 50 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        return result;
    }
}
```

### Enable Custom Repository Base Class

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.repository",
    repositoryBaseClass = BaseRepositoryImpl.class
)
public class JpaConfig {
}
```

---

## Transaction Management

### Service-Level Transactions

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // Read-only transaction - Hibernate optimizations enabled
    @Transactional(readOnly = true)
    public PostDto findBySlug(String slug) {
        return postRepository.findBySlugWithDetails(slug)
            .map(this::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("Post", "slug", slug));
    }

    // Write transaction with explicit rollback rules
    @Transactional(rollbackFor = Exception.class)
    public PostDto create(CreatePostRequest request, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (postRepository.existsBySlug(generateSlug(request.title()))) {
            throw new ConflictException("Post with this title already exists");
        }

        Post post = Post.create(request.title(), request.content(), user);
        Post saved = postRepository.persist(post);  // Use persist, not save

        log.info("Created post: id={}, slug={}", saved.getId(), saved.getSlug());
        return toDto(saved);
    }

    // Multi-step transaction
    @Transactional
    public void publishPost(Long postId, Long userId) {
        Post post = postRepository.findByIdWithUser(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", postId));

        if (!post.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not authorized to publish this post");
        }

        post.publish();  // State change - dirty checking handles update
    }
}
```

### Transaction Propagation

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // REQUIRES_NEW - independent transaction for audit/notification
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(Long userId, String message) {
        // Even if parent transaction fails, this notification is committed
        Notification notification = Notification.create(userId, message);
        notificationRepository.persist(notification);
    }

    // MANDATORY - must be called within existing transaction
    @Transactional(propagation = Propagation.MANDATORY)
    public void logActivity(Long userId, String action) {
        // Throws exception if no active transaction
    }
}
```

---

## Query Optimization

### Use Projections for Read-Only Operations

```java
// ❌ Bad: Fetches full entity for display
Post post = postRepository.findById(id).orElseThrow();
return new PostResponse(post.getId(), post.getTitle());

// ✅ Good: Fetch only needed fields
@Query("SELECT new com.example.dto.PostResponse(p.id, p.title) FROM Post p WHERE p.id = :id")
Optional<PostResponse> findResponseById(@Param("id") Long id);
```

### Keyset Pagination (Better than Offset)

```java
// ❌ Offset pagination - slow for large offsets
Page<Post> findByStatus(PostStatus status, Pageable pageable);

// ✅ Keyset pagination - consistent performance
@Query("""
    SELECT p FROM Post p
    WHERE p.status = :status
    AND (p.createdAt < :lastCreatedAt
         OR (p.createdAt = :lastCreatedAt AND p.id < :lastId))
    ORDER BY p.createdAt DESC, p.id DESC
    """)
List<Post> findNextPage(
    @Param("status") PostStatus status,
    @Param("lastCreatedAt") LocalDateTime lastCreatedAt,
    @Param("lastId") Long lastId,
    Pageable pageable
);
```

---

## N+1 Prevention

### The Problem

```java
// ❌ N+1: 1 query for posts + N queries for each user
List<Post> posts = postRepository.findAll();
posts.forEach(p -> log.info("Author: {}", p.getUser().getName()));
```

### Solutions

```java
// Solution 1: JOIN FETCH in JPQL
@Query("SELECT DISTINCT p FROM Post p JOIN FETCH p.user")
List<Post> findAllWithUser();

// Solution 2: EntityGraph
@EntityGraph(attributePaths = {"user"})
List<Post> findByStatus(PostStatus status);

// Solution 3: Batch fetching (global or per-relation)
@OneToMany(mappedBy = "post")
@BatchSize(size = 20)  // Fetches 20 related entities per batch
private List<Comment> comments;

// Solution 4: Subselect fetching
@OneToMany(mappedBy = "user")
@Fetch(FetchMode.SUBSELECT)  // Single subquery for all relations
private List<Post> posts;
```

### Global Batch Fetching Configuration

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 20
```

---

## Batch Operations

### Batch Insert Configuration

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

### Batch Insert with Clear

```java
@Service
@RequiredArgsConstructor
public class DataImportService {

    private final PostRepository postRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void importPosts(List<CreatePostRequest> requests, User user) {
        int batchSize = 50;

        for (int i = 0; i < requests.size(); i++) {
            Post post = Post.create(
                requests.get(i).title(),
                requests.get(i).content(),
                user
            );
            entityManager.persist(post);

            // Flush and clear every batch to prevent memory issues
            if ((i + 1) % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
}
```

---

## Error Handling

### Database Exception Mapping

```java
@RestControllerAdvice
@Slf4j
public class DatabaseExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = "Data constraint violation";

        if (ex.getCause() instanceof ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            if (constraintName != null) {
                if (constraintName.contains("email")) {
                    message = "Email already exists";
                } else if (constraintName.contains("slug")) {
                    message = "Slug already exists";
                }
            }
        }

        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", message));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", "Resource was modified by another user. Please refresh and retry."));
    }

    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ErrorResponse> handlePersistence(PersistenceException ex) {
        log.error("Persistence error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("DATABASE_ERROR", "A database error occurred"));
    }
}
```

---

## Summary

| Pattern | When to Use |
|---------|-------------|
| BaseEntity | All entities - provides auditing, versioning |
| Soft Delete | Data must be recoverable, audit trails needed |
| Custom Repository | Batch operations, explicit persist/merge control |
| JOIN FETCH | Loading specific relations with entity |
| EntityGraph | Declarative relation loading |
| BatchSize | Global N+1 prevention |
| Projections | Read-only queries, API responses |
| Keyset Pagination | Large datasets, consistent performance |

---

**Sources:**
- [Vlad Mihalcea - Best Spring Data JpaRepository](https://vladmihalcea.com/best-spring-data-jparepository/)
- [Hibernate-SpringBoot - 300+ Best Practices](https://github.com/AnghelLeonard/Hibernate-SpringBoot)
- [Spring Boot Soft Delete Pattern](https://github.com/dzinot/spring-boot-jpa-data-rest-soft-delete)

**Related Files:**
- [SKILL.md](../SKILL.md)
- [services-and-repositories.md](services-and-repositories.md)
- [async-and-errors.md](async-and-errors.md)
