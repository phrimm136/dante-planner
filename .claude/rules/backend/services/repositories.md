---
paths:
  - "backend/**/repository/**/*.java"
  - "backend/**/*Repository.java"
  - "backend/**/entity/**/*.java"
  - "backend/**/domain/**/*.java"
---

# Repository & Entity Patterns

## Mandatory Rule

**SQL in Repository only** - Not in Service

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| SQL in Service | Move to Repository `@Query` |
| String concat in `@Query` | Use `@Param` |

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
