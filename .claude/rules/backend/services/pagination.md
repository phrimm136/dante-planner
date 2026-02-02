---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/repository/**/*.java"
  - "backend/**/controller/**/*.java"
---

# Pagination Patterns

## Mandatory for List Endpoints

**ALL list endpoints MUST use `Pageable` parameter**

## Repository Pattern

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Page<User> findAll(Pageable pageable);

    Page<User> findByStatus(String status, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.name LIKE %:search%")
    Page<User> searchByName(@Param("search") String search, Pageable pageable);
}
```

## Service Pattern

```java
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable)
            .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String query, Pageable pageable) {
        return userRepository.searchByName(query, pageable)
            .map(UserResponse::from);
    }
}
```

## Controller Pattern

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(userService.findAll(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UserResponse>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.search(q, pageable));
    }
}
```

## Page Configuration

```java
// Default settings
@PageableDefault(
    size = 20,           // Default page size
    page = 0,            // Default page number
    sort = "id",         // Default sort field
    direction = Sort.Direction.ASC
)
```

## Configuration

```properties
# application.properties
spring.data.web.pageable.default-page-size=20
spring.data.web.pageable.max-page-size=100
spring.data.web.pageable.one-indexed-parameters=false
```

## Response Structure

```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {...},
    "offset": 0
  },
  "totalPages": 5,
  "totalElements": 95,
  "last": false,
  "first": true,
  "numberOfElements": 20,
  "size": 20,
  "number": 0
}
```

## Query Parameters

```
GET /api/users?page=0&size=20&sort=name,asc&sort=createdAt,desc
```
