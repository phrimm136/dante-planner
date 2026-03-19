---
paths:
  - "backend/**/repository/**/*.java"
  - "backend/**/*Repository.java"
  - "backend/**/entity/**/*.java"
  - "backend/**/domain/**/*.java"
---

# Repository & Entity Patterns

## Mandatory Rules

- **SQL in Repository only** — not in Service
- **`@Param` always** — never string concatenation in `@Query`
- **`@EntityGraph` or `JOIN FETCH`** for associations — prevent N+1
- **`Pageable` on all list methods** — never return unbounded `List<Entity>`
- **Default `FetchType.LAZY`** on collections

## N+1 Prevention Decision Tree

```
Single entity with associations  →  @EntityGraph on repository method
Complex JPQL with filters        →  JOIN FETCH in @Query
Multiple independent collections →  @BatchSize(size = N)
Atomic counter update            →  @Modifying @Query "UPDATE ... SET x = x + 1"
```

## Atomic Operations

```java
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("UPDATE Planner p SET p.upvotes = p.upvotes + 1 WHERE p.id = :plannerId")
int incrementUpvotes(@Param("plannerId") UUID plannerId);
```

## Pessimistic Locking (read → check → write races)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Planner p JOIN FETCH p.user WHERE p.id = :plannerId")
Optional<Planner> findByIdForUpdate(@Param("plannerId") UUID plannerId);
```

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| SQL in Service | Move to Repository `@Query` |
| String concat in `@Query` | Use `@Param` |
| `List<Entity>` without Pageable | `Page<Entity>` with `Pageable` |
| `FetchType.EAGER` on collections | `FetchType.LAZY` + `@EntityGraph` |
| Native queries when JPQL suffices | JPQL (type-safe, respects entity graph) |
| Missing `countQuery` on paginated `@Query` | Add explicit `countQuery` |

## Repository Template

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.status = :status")
    List<User> findByStatus(@Param("status") String status);

    boolean existsByEmail(String email);
}
```

## Entity Template

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
```

**Reference:** `PlannerRepository.java`
