---
paths:
  - "backend/**/dto/**/*.java"
  - "backend/**/*Request.java"
  - "backend/**/*Response.java"
---

# DTO Patterns

## Mandatory Rule

**Use DTOs, not entities** — never expose JPA entities in API

## Record vs Class Decision

| Use Case | Type | Reason |
|---|---|---|
| Request body (immutable input) | `record` | Compact, auto-validated, immutable |
| Response (simple projection) | `record` | No mutation needed |
| Response (entity mapping, builder) | `@Data @Builder class` | Builder pattern + Lombok |
| Nested/complex with optional fields | `@Data @Builder class` | Nullable builder fields |

## Naming

```
Request:   CreatePlannerRequest, UpdatePlannerRequest
Response:  PlannerResponse, PlannerSummaryResponse, PublicPlannerResponse
Sub-DTO:   PlannerConfigResponse (nested inside a response)
```

## Mapping Strategy

- Use static factory `fromEntity()` on the DTO class
- Never put mapping logic in Controller or Service
- For 5+ entity→DTO mappings across the project, consider MapStruct

## Nested DTO Validation

```java
// @Valid required to cascade validation into nested DTOs
public record CreatePlannerRequest(
    @NotBlank @Size(max = 100) String title,
    @NotNull Integer contentVersion,
    @Valid @NotNull PlannerContentRequest content
) {}
```

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| Return `Entity` | Return `ResponseDTO` |
| Shared DTO for request and response | Separate DTOs (different lifecycles) |
| `@Transient`/`@JsonIgnore` on entity as DTO substitute | Proper DTO |
| Omitting `@Valid` on nested DTO fields | Add `@Valid` — Jakarta does not cascade without it |

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
