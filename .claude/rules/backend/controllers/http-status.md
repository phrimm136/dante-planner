---
paths:
  - "backend/**/controller/**/*.java"
  - "backend/**/*Controller.java"
---

# HTTP Status Guidelines

| Operation | Success Status | Error Status |
|-----------|---------------|--------------|
| POST (create) | 201 CREATED | 400 BAD_REQUEST, 409 CONFLICT |
| GET (read) | 200 OK | 404 NOT_FOUND |
| PUT (update) | 200 OK | 400 BAD_REQUEST, 404 NOT_FOUND |
| DELETE | 204 NO_CONTENT | 404 NOT_FOUND |
| PATCH (partial) | 200 OK | 400 BAD_REQUEST, 404 NOT_FOUND |

## Examples

```java
// 201 CREATED
return ResponseEntity.status(HttpStatus.CREATED).body(created);

// 200 OK
return ResponseEntity.ok(user);

// 204 NO_CONTENT
return ResponseEntity.noContent().build();
```
