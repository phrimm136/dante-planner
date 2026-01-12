---
name: be-testing
description: Backend testing with JUnit and Mockito.
---

# Backend Testing Patterns

## Rules

- **Test behavior, not implementation** - Public API, not internals
- **Test edge cases** - Error paths, boundaries
- **Use builders/fixtures** - Not hardcoded data
- **Mock at boundaries** - External services, not internals

## Test Types

| Type | Annotation | Use Case |
|------|------------|----------|
| Unit | `@ExtendWith(MockitoExtension.class)` | Service logic |
| Repository | `@DataJpaTest` | JPA queries |
| Controller | `@WebMvcTest` | API endpoints |
| Integration | `@SpringBootTest` | Full stack |

## Unit Test Template

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

## Repository Test Template

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

## Controller Test Template (Jakarta Validation)

```java
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getUser_WhenExists_Returns200() throws Exception {
        when(userService.findById(1L))
            .thenReturn(new UserResponse(1L, "Test", "test@example.com"));

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test"));
    }

    @Test
    void createUser_WithInvalidData_Returns400() throws Exception {
        // Jakarta Validation triggers on @Valid @RequestBody
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\", \"email\": \"invalid\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

## Validation Imports (Jakarta)

```java
// Use Jakarta Validation (not javax)
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
```

## Patterns from Experience

- **Entity setters over Thread.sleep:** Use `entity.setCreatedAt(now.minusSeconds(10))` for deterministic ordering
- **AFTER_COMMIT limitation:** @Transactional rollback prevents @TransactionalEventListener(AFTER_COMMIT) from firing - mark tests @Disabled
- **Containerized tests:** Use `@Tag("containerized")` for MySQL-specific tests; run with `-Dgroups=containerized`

## Reference

- Run: `./mvnw test`
- Pattern: `PlannerServiceTest.java`, `PlannerControllerTest.java`
- Why: `docs/learning/backend-patterns.md`
