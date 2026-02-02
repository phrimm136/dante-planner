---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/repository/**/*.java"
  - "backend/**/entity/**/*.java"
---

# N+1 Query Prevention

## Problem

```java
// BAD: N+1 query - loads users, then N queries for each user's orders
@Transactional(readOnly = true)
public List<UserResponse> findAll() {
    return userRepository.findAll().stream()
        .map(user -> new UserResponse(
            user.getId(),
            user.getName(),
            user.getOrders().size()  // Triggers N queries!
        ))
        .toList();
}
```

## Solution 1: @EntityGraph (Recommended)

```java
public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = {"orders", "orders.items"})
    List<User> findAll();

    @EntityGraph(attributePaths = {"orders"})
    Optional<User> findById(Long id);
}
```

## Solution 2: JOIN FETCH

```java
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.id = :id")
    Optional<User> findByIdWithOrders(@Param("id") Long id);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.orders")
    List<User> findAllWithOrders();
}
```

## Entity Configuration

```java
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // LAZY by default for @OneToMany - prevents auto-loading
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();
}
```

## Batch Fetching (Alternative)

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @BatchSize(size = 10)  // Fetches 10 collections at once
    private List<Order> orders = new ArrayList<>();
}
```

## Monitoring (Development)

```properties
# application-dev.properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

## Common Patterns

### Load User with Orders
```java
@EntityGraph(attributePaths = {"orders"})
Optional<User> findById(Long id);
```

### Load User with Orders and Items
```java
@EntityGraph(attributePaths = {"orders", "orders.items"})
Optional<User> findByIdWithOrdersAndItems(Long id);
```

### Load Multiple Associations
```java
@EntityGraph(attributePaths = {"orders", "address", "profile"})
List<User> findAllComplete();
```
