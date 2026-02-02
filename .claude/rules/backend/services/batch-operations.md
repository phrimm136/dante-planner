---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/repository/**/*.java"
---

# Batch Operations Patterns

## Batch Insert (Save Multiple Entities)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    public List<UserResponse> createBatch(List<CreateUserRequest> requests) {
        List<User> users = requests.stream()
            .map(req -> new User(req.name(), req.email()))
            .toList();

        // JPA batches inserts automatically if configured
        List<User> saved = userRepository.saveAll(users);

        return saved.stream()
            .map(UserResponse::from)
            .toList();
    }
}
```

## Batch Configuration

```properties
# application.properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.batch_versioned_data=true
```

## Batch Update (Bulk Update Query)

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id IN :ids")
    int updateStatusBatch(@Param("ids") List<Long> ids, @Param("status") String status);

    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.lastLoginDate < :cutoffDate")
    int deactivateInactiveUsers(@Param("cutoffDate") LocalDate cutoffDate);
}

// Service usage
@Transactional
public int deactivateInactiveUsers(int daysInactive) {
    LocalDate cutoffDate = LocalDate.now().minusDays(daysInactive);
    return userRepository.deactivateInactiveUsers(cutoffDate);
}
```

## Batch Delete

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("DELETE FROM User u WHERE u.id IN :ids")
    int deleteBatch(@Param("ids") List<Long> ids);

    // Or use JPA method
    void deleteAllByIdInBatch(List<Long> ids);
}
```

## Processing Large Datasets (Streaming)

```java
@Transactional(readOnly = true)
public void processAllUsers() {
    try (Stream<User> userStream = userRepository.streamAll()) {
        userStream
            .filter(user -> user.isActive())
            .forEach(user -> {
                // Process each user
                processUser(user);
            });
    }
}

public interface UserRepository extends JpaRepository<User, Long> {
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "50"))
    Stream<User> streamAll();
}
```

## Chunk Processing (Pagination)

```java
@Transactional
public void processAllUsersInChunks() {
    int pageSize = 100;
    int pageNumber = 0;
    Page<User> page;

    do {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        page = userRepository.findAll(pageable);

        // Process chunk
        page.getContent().forEach(user -> {
            processUser(user);
        });

        pageNumber++;
    } while (page.hasNext());
}
```

## Performance Tips

| Pattern | Use Case | Performance |
|---------|----------|-------------|
| `saveAll()` with batch config | Insert < 1000 records | Fast with batching |
| Bulk UPDATE query | Update thousands | Very fast (single query) |
| Streaming | Read-only, large dataset | Memory efficient |
| Chunk processing | Update large dataset | Balanced |

## Batch Size Guidelines

```properties
# Small batches for transactional consistency
hibernate.jdbc.batch_size=20

# Larger batches for bulk operations
hibernate.jdbc.batch_size=100
```
