---
paths:
  - "backend/src/test/**/*ServiceTest.java"
  - "backend/src/test/**/*Test.java"
---

# Unit Testing Patterns

## Mandatory Rules

- **Test behavior, not implementation** — public API, not internals
- **Mock at boundaries** — external services, not internals

## Naming Convention

```
methodName_WhenCondition_ExpectedBehavior
findById_WhenExists_ReturnsUser
findById_WhenNotExists_ThrowsNotFound
castVote_WhenAlreadyVoted_Returns409
```

## Test Data Patterns

- Use a `TestDataFactory` static helper for building reusable entities
- For deterministic ordering: `entity.setCreatedAt(now.minusSeconds(10))` — never `Thread.sleep()`

## Template

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findById_WhenExists_ReturnsUser() {
        User user = new User("Test", "test@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse result = userService.findById(1L);

        assertThat(result.name()).isEqualTo("Test");
    }

    @Test
    void findById_WhenNotExists_ThrowsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(1L))
            .isInstanceOf(NotFoundException.class);
    }
}
```

**Reference:** `PlannerServiceTest.java`

## Secondary Test Instance Pattern

**Problem:** Tests needing different configuration (expired tokens, invalid signatures) create inline instances.

**Solution:** Store primary test config as instance variables (keypair, keys). Secondary instances reuse for most fields, customize only what differs.

**Example:** Invalid-signature test needs DIFFERENT RSA keypair. Expired-token test reuses SAME keypair, changes only expiry to 1ms.

**Reference:** `JwtTokenServiceTest.java` (lines 182-186, 209-213)
