---
name: be-service
description: Spring Boot service and repository patterns. Business logic, transactions, JPA queries.
---

# Backend Service Patterns

## Rules

- **`@Transactional` on class** - Default for service classes
- **`readOnly = true` for queries** - Optimize read operations
- **Use `.orElseThrow()`** - Never `.get()` on Optional
- **SQL in Repository only** - Not in Service

## Forbidden → Use Instead

| Forbidden | Use Instead |
|-----------|-------------|
| `optional.get()` | `.orElseThrow(() -> new NotFoundException())` |
| `@Transactional` on private | Only on public methods |
| SQL in Service | Move to Repository `@Query` |
| String concat in `@Query` | Use `@Param` |

## Service Template

```java
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userRepository.findById(id)
            .map(UserResponse::from)
            .orElseThrow(() -> new NotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
            .map(UserResponse::from)
            .toList();
    }

    public UserResponse create(CreateUserRequest request) {
        User user = new User(request.name(), request.email());
        return UserResponse.from(userRepository.save(user));
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User", id);
        }
        userRepository.deleteById(id);
    }
}
```

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

## Reference

- Pattern: `PlannerService.java`, `PlannerRepository.java`
- Why: `docs/learning/backend-patterns.md`
