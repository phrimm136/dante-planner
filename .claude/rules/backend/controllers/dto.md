---
paths:
  - "backend/**/dto/**/*.java"
  - "backend/**/*Request.java"
  - "backend/**/*Response.java"
---

# DTO Patterns

## Mandatory Rule

**Use DTOs, not entities** - Never expose JPA entities in API

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| Return `Entity` | Return `ResponseDTO` |

## Request DTO Template

```java
public record CreateUserRequest(
    @NotBlank String name,
    @Email String email,
    @Size(min = 8) String password
) {}
```

## Response DTO Template

```java
public record UserResponse(Long id, String name, String email) {
    public static UserResponse from(User entity) {
        return new UserResponse(
            entity.getId(),
            entity.getName(),
            entity.getEmail()
        );
    }
}
```
