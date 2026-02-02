---
paths:
  - "backend/src/test/**/*ServiceTest.java"
  - "backend/src/test/**/*Test.java"
---

# Unit Testing Patterns

## Mandatory Rules

- **Test behavior, not implementation** - Public API, not internals
- **Mock at boundaries** - External services, not internals

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
