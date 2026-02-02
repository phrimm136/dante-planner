---
paths:
  - "backend/**/controller/**/*.java"
  - "backend/**/*Controller.java"
  - "backend/**/dto/**/*.java"
---

# Validation Patterns

## Mandatory Rule

**Always `@Valid`** - Add to all `@RequestBody` parameters

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| `@RequestBody` without `@Valid` | `@Valid @RequestBody` |

## Usage

```java
@PostMapping
public ResponseEntity<UserResponse> create(
    @Valid @RequestBody CreateUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.create(request));
}
```

## Jakarta Validation Annotations

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
```
