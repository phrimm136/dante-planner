# JSON vs Schema Storage: Database Design Decisions

Choosing between JSON columns, normalized schemas, and document databases for complex application data.

---

## TL;DR

- **Hybrid approach (schema + JSON column)** is optimal for data with both queryable metadata and complex nested content
- **MySQL JSON is adequate** but slower than PostgreSQL JSONB or MongoDB
- **MongoDB is not worth migrating to** if you already have relational data and JPA infrastructure
- **Auth data belongs in relational databases** due to ACID requirements and referential integrity
- **Match storage strategy to access patterns** — not to data shape

---

## The Three Storage Strategies

### Option A: Full JSON Dump

Store everything in a single JSON column.

```java
@Column(columnDefinition = "JSON")
private String plannerData;
// Contains: title, category, status, content, timestamps, etc.
```

| Pros | Cons |
|------|------|
| Simple schema | Can't query by fields |
| Easy to add fields | Can't sort efficiently |
| Frontend/backend match exactly | No indexing |
| No migrations needed | Full read-modify-write |

### Option B: Full Schema (Normalized)

Separate tables for every nested structure.

```java
@Entity class Planner { ... }
@Entity class FloorSelection { ... }
@Entity class SinnerEquipment { ... }
@Entity class SkillEAState { ... }
@Entity class SectionNote { ... }
```

| Pros | Cons |
|------|------|
| Full query flexibility | Many tables (15+) |
| Relational integrity | Complex JOINs |
| Easy partial updates | Schema migrations |
| Analytics-friendly | Mapping complexity |

### Option C: Hybrid (Recommended)

Queryable fields in schema, complex nested content in JSON.

```java
// Queryable fields in schema columns
private String title;
private String category;
private Instant lastModifiedAt;

// Complex nested content in JSON
@Column(columnDefinition = "JSON")
private String content;  // MDPlannerContent serialized
```

| Pros | Cons |
|------|------|
| Query by key fields | Can't query inside JSON |
| Sort by timestamps | Full content reload on edit |
| Simple schema (1 table) | App-level JSON validation |
| Flexible content evolution | JSON parsing overhead |

---

## This Project's Approach (Planner Entity)

The `Planner` entity uses the hybrid approach:

```java
@Entity
@Table(name = "planners")
public class Planner {
    // Schema columns - indexed, queryable
    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    // JSON column - complex nested structure
    @Column(columnDefinition = "JSON", nullable = false)
    private String content;  // MDPlannerContent with 10+ nested fields

    // Schema version for forward migration
    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;
}
```

**Why this works:**

1. **Query patterns match storage**: List pages query by `user_id`, `category`, `lastModifiedAt` — all schema columns
2. **Content is loaded atomically**: Detail page loads entire planner, edits entire planner, saves entire planner
3. **Schema evolution is handled**: `schemaVersion` enables migration when JSON structure changes
4. **Content structure is complex**: `MDPlannerContent` has 15+ fields including nested objects and arrays

---

## MySQL JSON vs PostgreSQL JSONB vs MongoDB

| Feature | MySQL JSON | PostgreSQL JSONB | MongoDB |
|---------|------------|------------------|---------|
| Storage format | Text-based | Binary (faster) | Binary BSON |
| Index JSON fields | Generated columns | GIN index | Native |
| Array element index | Multi-valued (8.0.17+) | GIN | Native |
| Partial update | `JSON_SET()` (rewrites) | `jsonb_set()` (rewrites) | True partial `$set` |
| Query syntax | `->`, `->>`, `JSON_EXTRACT()` | `->`, `->>`, `@>` | Dot notation |
| Performance | Slower parsing | Fast binary ops | Fast native |

### MySQL JSON Indexing (Current Stack)

```sql
-- MySQL requires generated columns for JSON indexing
ALTER TABLE planners
ADD COLUMN title_gen VARCHAR(255) GENERATED ALWAYS AS (content->>'$.title'),
ADD INDEX idx_title_gen (title_gen);
```

### PostgreSQL JSONB Indexing

```sql
-- PostgreSQL: Direct GIN index on entire JSONB
CREATE INDEX idx_content ON planners USING GIN (content);

-- Query inside JSON directly
SELECT * FROM planners
WHERE content @> '{"selectedGiftIds": ["9001"]}';
```

### MongoDB Indexing

```javascript
// MongoDB: Direct index on nested field
db.planners.createIndex({ "content.selectedGiftIds": 1 })

// Query with dot notation
db.planners.find({ "content.selectedGiftIds": "9001" })
```

---

## When to Consider MongoDB

### MongoDB Makes Sense When:

| Scenario | Why MongoDB Helps |
|----------|-------------------|
| Frequent partial updates | True `$set` on nested paths |
| Variable document structure | Each document can differ |
| Deep nesting queries | Native dot notation queries |
| No relational needs | Standalone documents |
| Greenfield project | No migration cost |
| Horizontal scaling | Built-in sharding |

### Stay with MySQL/PostgreSQL When:

| Scenario | Why Relational Wins |
|----------|---------------------|
| Existing JPA codebase | Migration cost is high |
| Relational data exists | User ↔ Planner foreign keys |
| ACID transactions needed | Multi-document transactions limited |
| Query by schema fields | Already indexed, fast |
| Load/save entire documents | No benefit from partial updates |

---

## Auth Data: Why Relational Databases Win

Authentication data has specific requirements that favor relational databases:

### 1. Unique Constraints

```sql
-- MySQL: DB enforces uniqueness
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) UNIQUE,  -- Constraint violation on duplicate
    username VARCHAR(50) UNIQUE
);
```

```javascript
// MongoDB: Unique index (similar but subtle differences)
db.users.createIndex({ email: 1 }, { unique: true })
// Race condition window between check and insert
```

### 2. Relational Role Hierarchy

```sql
-- Clean relational model
users → user_roles → roles → role_permissions → permissions

-- Single query for all user permissions
SELECT p.* FROM permissions p
JOIN role_permissions rp ON p.id = rp.permission_id
JOIN user_roles ur ON rp.role_id = ur.role_id
WHERE ur.user_id = ?;
```

```javascript
// MongoDB: Embedded (denormalized, duplicated)
{
    _id: 1,
    email: "user@example.com",
    roles: ["admin", "user"],
    permissions: ["read", "write", "delete"]  // Duplicated across users
}
// Or: References requiring multiple queries / $lookup
```

### 3. ACID Transactions

```sql
-- MySQL: Atomic user creation with role
START TRANSACTION;
INSERT INTO users (id, email) VALUES (1, 'user@example.com');
INSERT INTO user_roles (user_id, role_id) VALUES (1, 'ROLE_USER');
COMMIT;
-- All or nothing, guaranteed
```

```javascript
// MongoDB (4.0+): Multi-document transaction (more overhead)
const session = client.startSession();
session.withTransaction(async () => {
    await users.insertOne({ _id: 1, email: 'user@example.com' }, { session });
    await userRoles.insertOne({ userId: 1, roleId: 'ROLE_USER' }, { session });
});
```

### Auth Database Recommendation

| Database | Auth Suitability | Best For |
|----------|------------------|----------|
| MySQL/PostgreSQL | Excellent | Complex RBAC, audit trails, compliance |
| MongoDB | Adequate | Simple auth, JWT-based, MongoDB-only stacks |
| Dedicated (Keycloak) | Excellent | Enterprise, SSO, OAuth/OIDC |

---

## Decision Framework

### Storage Strategy Decision

| Question | If YES → | If NO → |
|----------|----------|---------|
| Need to query inside content? | Normalize or JSONB + GIN | Keep JSON |
| Need partial field updates? | Consider normalizing | Keep JSON |
| Content structure changes often? | Keep JSON | Either works |
| Need referential integrity inside? | Normalize | Keep JSON |
| Content has recursive depth? | Keep JSON | Either works |

### Database Choice Decision

| Question | If YES → | If NO → |
|----------|----------|---------|
| Have existing relational DB? | Stay relational | Consider MongoDB |
| Need foreign key relationships? | Relational | MongoDB possible |
| Query inside JSON frequently? | PostgreSQL JSONB or MongoDB | MySQL JSON adequate |
| True partial nested updates? | MongoDB only | Any DB works |
| Complex RBAC/auth? | Relational | Either works |

---

## Practical Patterns

### Pattern 1: Hybrid Relational (Current)

```
┌─────────────────────────────────────────────┐
│                   MySQL                      │
│  ┌─────────────┐    ┌──────────────────┐    │
│  │   users     │    │    planners      │    │
│  │  (schema)   │◄───│ schema + JSON    │    │
│  └─────────────┘    └──────────────────┘    │
└─────────────────────────────────────────────┘
```

### Pattern 2: Polyglot Persistence

```
┌─────────────────┐         ┌─────────────────┐
│  MySQL          │         │    MongoDB      │
│  ─────────────  │         │  ─────────────  │
│  • users        │────────▶│  • planners     │
│  • roles        │  userId │  • game_data    │
│  • sessions     │         │  • user_prefs   │
└─────────────────┘         └─────────────────┘
```

### Pattern 3: Extract on Query Need

When you later need to query by something inside `content`:

```java
// Add extracted column to schema
@Column(name = "primary_identity_id")
private Integer primaryIdentityId;  // Extracted from content

// Update on save
public void setContent(String content) {
    this.content = content;
    MDPlannerContent parsed = parse(content);
    this.primaryIdentityId = parsed.getEquipment()
        .get("sinner1").getIdentityId();
}
```

---

## Summary Table

| Data Type | Recommended Storage | Reasoning |
|-----------|---------------------|-----------|
| User/Auth | Relational schema | ACID, unique constraints, RBAC |
| Planner metadata | Schema columns | Query, sort, index |
| Planner content | JSON column | Complex, variable, loaded atomically |
| Queryable nested field | Extract to schema | Best of both worlds |
| Audit logs | JSON or schema | Depends on query needs |
| User preferences | JSON column | Varies per user, rarely queried |

---

## Key Takeaways

1. **Hybrid is usually right**: Schema for queryable fields, JSON for complex content
2. **MySQL JSON is adequate**: Hybrid approach compensates for limitations
3. **MongoDB migration rarely worth it**: High cost, marginal benefit for existing apps
4. **Auth stays relational**: ACID and referential integrity matter
5. **Access patterns drive decisions**: Not data shape

---

## References

- [MySQL JSON Functions](https://dev.mysql.com/doc/refman/8.0/en/json-functions.html)
- [PostgreSQL JSONB](https://www.postgresql.org/docs/current/datatype-json.html)
- [MongoDB vs PostgreSQL](https://www.mongodb.com/compare/mongodb-postgresql)
- [When to Use NoSQL](https://martinfowler.com/articles/nosqlIntro.html)
