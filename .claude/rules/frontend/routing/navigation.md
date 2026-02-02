---
paths:
  - "frontend/src/routes/**/*.tsx"
  - "frontend/src/components/**/*.tsx"
---

# Navigation Patterns

## Mandatory Rule

**Use `Link` component** - Not `<a href>`

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| `<a href="/path">` | `<Link to="/path">` |

## Navigation Examples

```typescript
import { Link, useNavigate } from '@tanstack/react-router'

// Declarative
<Link to="/identity/$id" params={{ id: '10101' }}>View</Link>

// Programmatic
const navigate = useNavigate()
navigate({ to: '/identity/$id', params: { id } })
```
