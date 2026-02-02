---
paths:
  - "frontend/src/hooks/**/*.ts"
---

# Query StaleTime Guidelines

## StaleTime by Data Type

| Data Type | StaleTime | Reason |
|-----------|-----------|--------|
| Static game data (specs) | 5 minutes | Rarely changes, frequently accessed |
| i18n translations | 7 days | Almost never changes per deployment |
| User settings/preferences | 30 seconds | May change during session |
| Real-time data (SSE) | 0 | Always fresh |

## Example

```typescript
queries: [
  {
    queryKey: ['identity', id],
    queryFn: fetchIdentitySpec,
    staleTime: 5 * 60 * 1000,  // 5 minutes
  },
  {
    queryKey: ['identity', id, 'i18n', language],
    queryFn: fetchIdentityI18n,
    staleTime: 7 * 24 * 60 * 60 * 1000,  // 7 days
  },
]
```
