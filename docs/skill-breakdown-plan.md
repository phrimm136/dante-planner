# Skill Breakdown Plan

## Problem Statement

Current skills are monolithic and token-expensive:
- **Backend**: 395 lines SKILL.md + 12,843 lines resources = ~13,200 lines
- **Frontend**: 215 lines SKILL.md + 6,331 lines resources = ~6,500 lines

Loading a skill burns tokens even when only a subset is needed.

## Goal

Break skills into smaller, task-focused units where:
1. **SKILL.md** = Description + triggers + resource map (no code examples)
2. **Resources** = All code examples and detailed patterns
3. **CLAUDE.md** = Access controller pointing to specific resources

---

## Proposed Skill Structure

### Frontend Skills (Split by Task Type)

| Skill Name | Description | Primary Resources |
|------------|-------------|-------------------|
| `fe-component` | Component creation patterns | `component-patterns.md`, `styling-guide.md` |
| `fe-data` | Data fetching and validation | `data-fetching.md`, `schemas-and-validation.md` |
| `fe-routing` | Page and route creation | `routing-guide.md`, `file-organization.md` |
| `fe-testing` | Frontend testing patterns | `testing-guide.md` |

### Backend Skills (Split by Task Type)

| Skill Name | Description | Primary Resources |
|------------|-------------|-------------------|
| `be-controller` | REST controller patterns | `routing-and-controllers.md`, `validation-patterns.md` |
| `be-service` | Service and repository patterns | `services-and-repositories.md`, `database-patterns.md` |
| `be-security` | Authentication and authorization | `security-guide.md` |
| `be-async` | Async, WebSocket, error handling | `async-and-errors.md`, `websocket-guide.md` |
| `be-testing` | Backend testing patterns | `testing-guide.md` |
| `be-config` | Configuration and monitoring | `configuration.md`, `sentry-and-monitoring.md` |

---

## New SKILL.md Template

Each skill will follow this lean structure (~30-50 lines max):

```markdown
---
name: skill-name
description: Brief description for trigger matching
---

# Skill Name

## Purpose
One paragraph description.

## When to Use
- Bullet list of triggers

## Pattern Enforcement (MANDATORY)
| New File | MUST Read First |
|----------|-----------------|
| ... | ... |

## Forbidden Patterns
| Pattern | Use Instead |
|---------|-------------|
| ... | ... |

## Resources
- [resource-name.md](resources/resource-name.md) - Brief description
```

**What moves to resources:**
- All code examples
- Detailed checklists
- Common imports
- Anti-patterns with examples
- Quick references with code

---

## Migration Plan

### Phase 1: Create New Skill Structure

1. Create new skill directories:
   ```
   .claude/skills/
   ├── fe-component/
   │   ├── SKILL.md (~40 lines)
   │   └── resources/ (symlink or copy relevant files)
   ├── fe-data/
   ├── fe-routing/
   ├── fe-testing/
   ├── be-controller/
   ├── be-service/
   ├── be-security/
   ├── be-async/
   ├── be-testing/
   └── be-config/
   ```

2. Each SKILL.md contains:
   - Description (for trigger matching)
   - When to use (trigger hints)
   - Pattern enforcement table (minimal)
   - Forbidden patterns (minimal)
   - Resource links (for on-demand reading)

### Phase 2: Redistribute Resources

**Option A: Shared Resources (Recommended)**
- Keep resources in central location: `.claude/skills/resources/frontend/`, `.claude/skills/resources/backend/`
- Skills reference shared resources
- Avoids duplication

**Option B: Duplicated Resources**
- Each skill has its own resources folder
- Some duplication but fully self-contained

### Phase 3: Update Triggers

Update `skill-rules.json` with new skill triggers:

```json
{
  "fe-component": {
    "triggers": ["components/**/*.tsx", "creating component"],
    "priority": 1
  },
  "fe-data": {
    "triggers": ["hooks/use*.ts", "schemas/*.ts", "data fetching"],
    "priority": 1
  },
  "be-controller": {
    "triggers": ["*Controller.java", "creating endpoint"],
    "priority": 1
  }
}
```

### Phase 4: Update Domain CLAUDE.md

Keep `frontend/CLAUDE.md` and `backend/CLAUDE.md` as access controllers:
- Point to specific skills by task
- Resource map remains as-is
- Remove "load skill first" instruction (skills are now lightweight)

### Phase 5: Deprecate Old Skills

1. Keep old skills temporarily for fallback
2. After validation, remove `frontend-dev-guidelines` and `backend-dev-guidelines`

---

## Token Savings Estimate

| Scenario | Current Load | New Load | Savings |
|----------|--------------|----------|---------|
| Creating FE component | ~6,500 lines | ~40 (skill) + ~500 (component-patterns.md) | ~92% |
| Creating BE controller | ~13,200 lines | ~40 (skill) + ~1,000 (routing-and-controllers.md) | ~92% |
| Creating data hook | ~6,500 lines | ~40 (skill) + ~500 (data-fetching.md) + ~500 (schemas.md) | ~84% |

---

## Decisions Made

| Question | Decision |
|----------|----------|
| **Resource location** | Duplicated per skill (fully self-contained) |
| **Trigger overlap** | Multiple skills can match simultaneously |
| **Old skill handling** | Keep as deprecated, rename with `OLD-` prefix |

---

## Next Steps

1. [x] Confirm structure approach → Duplicated per skill
2. [ ] Create first skill (`fe-component`) as prototype
3. [ ] Create remaining FE skills (fe-data, fe-routing, fe-testing)
4. [ ] Create BE skills (be-controller, be-service, be-security, be-async, be-testing, be-config)
5. [ ] Update skill-rules.json with new triggers
6. [ ] Rename old skills to OLD- prefix
