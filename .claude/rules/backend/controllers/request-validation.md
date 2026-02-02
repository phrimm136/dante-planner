---
paths:
  - "backend/**/controller/**/*.java"
  - "backend/**/dto/**/*.java"
---

# Request Validation Patterns

## Two-Layer Validation

1. **Format Validation** (Controller) - Bean Validation annotations
2. **Business Validation** (Service) - Business rules

## Common Validation Annotations

```java
import jakarta.validation.constraints.*;

public record CreateUserRequest(
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
             message = "Password must contain letters and numbers")
    String password,

    @NotNull(message = "Age is required")
    @Min(value = 18, message = "Must be at least 18")
    @Max(value = 120, message = "Must be at most 120")
    Integer age,

    @NotNull(message = "Role is required")
    Role role,

    @Past(message = "Birth date must be in the past")
    LocalDate birthDate,

    @URL(message = "Invalid URL")
    String website
) {}
```

## Nested Object Validation

```java
public record CreateOrderRequest(
    @NotNull(message = "Customer is required")
    @Valid  // Important: validate nested object
    CustomerInfo customer,

    @NotEmpty(message = "At least one item is required")
    @Valid  // Important: validate each item in list
    List<OrderItem> items
) {}

public record CustomerInfo(
    @NotBlank String name,
    @Email String email
) {}

public record OrderItem(
    @NotBlank String productId,
    @Positive Integer quantity
) {}
```

## Controller Usage

```java
@PostMapping
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    // Format validation already passed
    // Now perform business validation in service
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.create(request));
}
```

## Custom Validation

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "Email already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
@RequiredArgsConstructor
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    private final UserRepository userRepository;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email == null || !userRepository.existsByEmail(email);
    }
}

// Usage
public record CreateUserRequest(
    @UniqueEmail String email
) {}
```

## Validation Groups

```java
public interface Create {}
public interface Update {}

public record UserRequest(
    @Null(groups = Create.class)  // ID must be null on create
    @NotNull(groups = Update.class)  // ID required on update
    Long id,

    @NotBlank(groups = {Create.class, Update.class})
    String name
) {}

// Controller
@PostMapping
public ResponseEntity<User> create(
    @Validated(Create.class) @RequestBody UserRequest request) {
    // ...
}

@PutMapping("/{id}")
public ResponseEntity<User> update(
    @PathVariable Long id,
    @Validated(Update.class) @RequestBody UserRequest request) {
    // ...
}
```

## Validation Error Response

```java
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "Invalid value"
            ));

        return ResponseEntity.badRequest()
            .body(new ValidationErrorResponse("VALIDATION_ERROR", errors));
    }
}

public record ValidationErrorResponse(
    String code,
    Map<String, String> errors
) {}
```

## Jakarta Validation Import

```java
// Use Jakarta (not javax)
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
```
