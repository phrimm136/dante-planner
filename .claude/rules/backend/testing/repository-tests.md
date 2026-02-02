---
paths:
  - "backend/src/test/**/*RepositoryTest.java"
---

# Repository Testing Patterns

## Template

```java
@DataJpaTest
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_WhenExists_ReturnsUser() {
        userRepository.save(new User("Test", "test@example.com"));

        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test");
    }
}
```

## Patterns from Experience

- **Entity setters over Thread.sleep:** Use `entity.setCreatedAt(now.minusSeconds(10))` for deterministic ordering
- **AFTER_COMMIT limitation:** @Transactional rollback prevents @TransactionalEventListener(AFTER_COMMIT) from firing - mark tests @Disabled
- **Containerized tests:** Use `@Tag("containerized")` for MySQL-specific tests; run with `-Dgroups=containerized`
