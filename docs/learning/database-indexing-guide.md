# Database Indexing: Column Order and Performance

Understanding how index column order dramatically affects query performance.

---

## TL;DR

- Index column order **must match query patterns** (leftmost prefix rule)
- Put **equality conditions before range conditions**
- An index on `(A, B, C)` cannot efficiently serve `WHERE B = ?`
- Wrong order = full table scan = slow queries

---

## How B-Tree Indexes Work

Think of an index like a **sorted phone book**:

```
Index on (last_name, first_name):

Adams, Alice
Adams, Bob
Baker, Carol
Baker, Dave
Smith, Alice
Smith, Bob
Smith, Carol
```

**What's fast:**
- Find "Smith" → Jump directly to S section
- Find "Smith, Bob" → Jump to S, then to B within Smith

**What's slow:**
- Find all "Bob" → Must scan entire book (no "Bob" section exists)

This is the **leftmost prefix rule** in action.

---

## The Leftmost Prefix Rule

For a composite index on `(A, B, C)`:

| Query Pattern | Uses Index? | Explanation |
|---------------|-------------|-------------|
| `WHERE A = 1` | ✅ Yes | Uses leftmost column |
| `WHERE A = 1 AND B = 2` | ✅ Yes | Uses left-to-right |
| `WHERE A = 1 AND B = 2 AND C = 3` | ✅ Yes | Uses all columns |
| `WHERE B = 2` | ❌ No | **Skips A** - can't use index |
| `WHERE C = 3` | ❌ No | **Skips A, B** - can't use index |
| `WHERE A = 1 AND C = 3` | ⚠️ Partial | Uses A only, C requires scan |
| `WHERE B = 2 AND C = 3` | ❌ No | **Skips A** - can't use index |

**Key insight:** You can stop at any point, but you cannot skip columns.

---

## Equality Before Range

Range conditions (`>`, `<`, `BETWEEN`, `LIKE 'prefix%'`) **stop index traversal**.

### Bad: Range before equality

```sql
-- Index: (created_at, status)
SELECT * FROM orders
WHERE created_at > '2024-01-01'
  AND status = 'PENDING';
-- Uses created_at, but status requires row-by-row check
```

### Good: Equality before range

```sql
-- Index: (status, created_at)
SELECT * FROM orders
WHERE status = 'PENDING'
  AND created_at > '2024-01-01';
-- Uses both columns efficiently
```

**Rule:** Put `=` conditions first, then `>`, `<`, `BETWEEN`.

---

## ORDER BY and Index Usage

Indexes can also optimize sorting, but order matters:

```sql
-- Index: (user_id, created_at)

-- ✅ Uses index for both WHERE and ORDER BY
SELECT * FROM posts
WHERE user_id = 123
ORDER BY created_at DESC;

-- ❌ Cannot use index for ORDER BY (filesort required)
SELECT * FROM posts
WHERE user_id = 123
ORDER BY title;
```

---

## Practical Examples

### Example 1: User Activity Log

```sql
-- Query patterns:
-- 1. Get user's recent activities
-- 2. Get all activities of a type for a user
-- 3. Get activities in date range for a user

-- ❌ Bad index
CREATE INDEX idx_activity ON user_activity(created_at, user_id, type);
-- Query 1 & 2 skip created_at, can't use index

-- ✅ Good index
CREATE INDEX idx_activity ON user_activity(user_id, type, created_at);
-- All queries start with user_id ✓
```

### Example 2: E-commerce Products

```sql
-- Query: Find products by category, sorted by price
SELECT * FROM products
WHERE category_id = 5 AND in_stock = true
ORDER BY price;

-- ✅ Optimal index
CREATE INDEX idx_products ON products(category_id, in_stock, price);
-- Filters by category, then in_stock, then sorted by price
```

### Example 3: This Project (Planner Votes)

```sql
-- Entity: PlannerVote with composite key (user_id, planner_id)
-- Query patterns:
-- 1. Check if user voted on planner: WHERE user_id = ? AND planner_id = ?
-- 2. Count votes for planner: WHERE planner_id = ? AND deleted_at IS NULL
-- 3. Get user's votes: WHERE user_id = ? AND deleted_at IS NULL

-- Primary key index: (user_id, planner_id) - covers query 1 & 3
-- Additional index needed for query 2:
CREATE INDEX idx_planner_votes_planner ON planner_votes(planner_id, deleted_at);
```

---

## JPA/Spring Boot Index Definition

### Entity-level index

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user_status", columnList = "user_id, status, created_at")
})
public class Order {
    // Column order in columnList = index column order
}
```

### Flyway migration

```sql
-- V010__add_order_indexes.sql
CREATE INDEX idx_orders_user_status ON orders(user_id, status, created_at);

-- For covering index (includes data to avoid table lookup)
CREATE INDEX idx_orders_covering ON orders(user_id, status) INCLUDE (total_amount);
```

---

## Common Mistakes

### 1. Creating index per column instead of composite

```sql
-- ❌ Two separate indexes
CREATE INDEX idx_a ON table(A);
CREATE INDEX idx_b ON table(B);
-- Query "WHERE A = 1 AND B = 2" uses only ONE index

-- ✅ One composite index
CREATE INDEX idx_ab ON table(A, B);
-- Query uses both columns efficiently
```

### 2. Wrong column order

```sql
-- Query: WHERE status = 'ACTIVE' AND user_id = 123

-- ❌ Bad (low cardinality first)
CREATE INDEX idx ON users(status, user_id);
-- status has few values (ACTIVE/INACTIVE), scans many rows

-- ✅ Better (high cardinality first)
CREATE INDEX idx ON users(user_id, status);
-- user_id is unique, finds exact row fast
```

### 3. Ignoring NULL handling

```sql
-- Soft delete pattern: deleted_at IS NULL for active records

-- ❌ Index may not be used for IS NULL
CREATE INDEX idx ON records(deleted_at);

-- ✅ Use partial index (PostgreSQL) or composite
CREATE INDEX idx ON records(status, deleted_at) WHERE deleted_at IS NULL;
```

---

## Verification: EXPLAIN ANALYZE

Always verify index usage:

```sql
-- MySQL
EXPLAIN SELECT * FROM orders WHERE user_id = 123;

-- PostgreSQL
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 123;
```

Look for:
- `type: ref` or `type: range` (good) vs `type: ALL` (full scan, bad)
- `key: idx_name` (using index) vs `key: NULL` (not using index)
- `rows` estimate - lower is better

---

## Summary Table

| Principle | Do | Don't |
|-----------|-----|-------|
| Column order | Match query WHERE clause left-to-right | Arbitrary order |
| Equality vs Range | `=` columns first, then `>/<` | Range conditions first |
| Cardinality | Consider query patterns first | Always high cardinality first |
| Composite vs Single | One composite for related queries | Separate index per column |
| Verification | EXPLAIN ANALYZE every query | Assume index is used |

---

## References

- [Use The Index, Luke](https://use-the-index-luke.com/) - Comprehensive indexing guide
- [MySQL Index Documentation](https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html)
- [PostgreSQL Index Documentation](https://www.postgresql.org/docs/current/indexes.html)
