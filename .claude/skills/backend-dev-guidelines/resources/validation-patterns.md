# Validation Patterns – Input Validation with Spring Boot

Complete guide to input validation using **Jakarta Bean Validation**, **Spring MVC**, and optional **custom validators** for type-safe, maintainable validation.

## Table of Contents

* [Why Spring Validation?](#why-spring-validation)
* [Basic Validation Patterns](#basic-validation-patterns)
* [DTO-Centric Validation](#dto-centric-validation)
* [Controller-Level Validation](#controller-level-validation)
* [Global Error Handling](#global-error-handling)
* [Advanced Validation Patterns](#advanced-validation-patterns)
* [Testing Validation](#testing-validation)

---

## Why Spring Validation?

### Benefits

**Type Safety & Consistency**

* Compile-time DTO validation
* Runtime enforcement
* Strong IDE support

**Framework Integration**

* Native to Spring MVC
* Works with Spring Security
* Unified error handling

**Extensibility**

* Custom constraints
* Conditional validation
* Cross-field validation

---

## Basic Validation Patterns

### Primitive Constraints

```java
import jakarta.validation.constraints.*;

public class UserDTO {
    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    @Min(0)
    @Max(100)
    private Integer age;

    @NotNull
    private Boolean active;
}
```

### Common Annotations

| Annotation    | Purpose            |
| ------------- | ------------------ |
| `@NotNull`    | Must not be null   |
| `@NotBlank`   | Non-empty string   |
| `@Size`       | Length constraints |
| `@Email`      | Email format       |
| `@Min / @Max` | Numeric bounds     |
| `@Pattern`    | Regex validation   |

---

## DTO-Centric Validation

### Recommended Pattern

Validation rules live **inside DTOs**, not controllers.

```java
public class CreateUserRequest {

    @Email(message = "Invalid email address")
    @NotBlank
    private String email;

    @Size(min = 2, max = 100)
    private String name;

    @NotEmpty
    private List<@NotBlank String> roles;
}
```

**Benefits**

* Reusable
* Testable
* Clear contract between API & service layer

---

## Controller-Level Validation

### Automatic Validation with `@Valid`

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<User> createUser(
        @Valid @RequestBody CreateUserRequest request
    ) {
        return ResponseEntity.status(201)
            .body(userService.createUser(request));
    }
}
```

### Validation Flow

```
Request → DTO binding → Bean Validation → Controller → Service
```

If validation fails, Spring throws `MethodArgumentNotValidException` automatically.

---

## Global Error Handling

### Centralized Validation Error Formatter

```java
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.badRequest().body(Map.of(
            "message", "Validation failed",
            "errors", errors
        ));
    }
}
```

**Response Example**

```json
{
  "message": "Validation failed",
  "errors": {
    "email": "Invalid email address",
    "name": "size must be between 2 and 100"
  }
}
```

---

## Advanced Validation Patterns

### Cross-Field Validation

```java
@AssertTrue(message = "endDate must be after startDate")
public boolean isDateRangeValid() {
    return endDate.isAfter(startDate);
}
```

### Custom Constraint Annotation

```java
@Target({ FIELD })
@Retention(RUNTIME)
@Constraint(validatedBy = RoleValidator.class)
public @interface ValidRole {
    String message() default "Invalid role";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
public class RoleValidator implements ConstraintValidator<ValidRole, String> {
    private static final Set<String> ROLES = Set.of("admin", "user", "operations");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && ROLES.contains(value);
    }
}
```

### Conditional Validation

```java
public class WorkflowRequest {
    @NotNull
    private WorkflowType type;

    private Long entityId;

    @AssertTrue(message = "entityId required for UPDATE")
    public boolean isEntityIdValid() {
        return type != WorkflowType.UPDATE || entityId != null;
    }
}
```

---

## Validation in Services

> ❗ Services should **assume validated input**.

Only validate again if:

* Input does not come from API
* Data comes from external systems

---

## Testing Validation

### Controller Validation Test

```java
@WebMvcTest(UserController.class)
class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"invalid\" }")
            )
            .andExpect(status().isBadRequest());
    }
}
```

---

## Best Practices Summary

* ✅ Validate at DTO boundary
* ✅ Use `@Valid` + `@RestControllerAdvice`
* ✅ Keep services validation-free
* ✅ Prefer annotations over manual checks
* ❌ Avoid validation logic in controllers

---

**Related Files**

* `routing-and-controllers-springboot.md`
* `services-and-repositories-springboot.md`
* `testing-guide-springboot.md`
