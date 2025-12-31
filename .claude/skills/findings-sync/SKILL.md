---
name: findings-sync
description: Synchronize patterns from docs/**/findings.md to CLAUDE.md and skill documentation. Use when updating specs, consolidating learnings, preventing outdated documentation, or running /findings-sync command.
---

# Findings Synchronization Workflow

## Purpose

Extract patterns, recommendations, and learnings from `docs/**/findings.md` files and update the corresponding skill documentation to keep specs current and prevent knowledge decay.

## When to Use This Skill

- After completing a feature implementation
- When running `/findings-sync` command
- Before major refactoring to capture existing patterns
- Periodically (e.g., weekly) to consolidate learnings

---

## Workflow Overview

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  1. COLLECT     │────▶│   2. ANALYZE    │────▶│   3. PROPOSE    │
│  Scan findings  │     │  Extract & diff │     │  Generate diffs │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │   4. APPLY      │
                                                │ Update skills   │
                                                └─────────────────┘
```

---

## Step 1: COLLECT - Scan Findings Files

### Priority Order
Files are processed by recency (higher directory numbers = newer):
```
docs/06-planner-creation/08-skill-replacement/  (newest)
docs/06-planner-creation/07-comprehensive-ego-gift-list/
docs/05-code-refactoring/...
docs/04-ego-gift-browser/...
... (older)
```

### Key Sections to Extract
| Section | Purpose | Target |
|---------|---------|--------|
| **Pattern Recommendations** | New patterns to document | Skill resources |
| **Key Learnings** | Important discoveries | CLAUDE.md or skills |
| **Things to Watch** | Potential issues | Skill warnings |
| **Next Time** | Process improvements | Workflow docs |

---

## Step 2: ANALYZE - Categorize Patterns

### Pattern Categories

**1. Frontend Patterns** → `frontend-dev-guidelines`
- Component patterns
- State management
- Data fetching
- Performance optimization

**2. Backend Patterns** → `backend-dev-guidelines`
- Controller/Service patterns
- Database patterns
- Validation patterns

**3. Process Patterns** → `CLAUDE.md`
- Pre-write checklists
- Review processes
- Breaking change protocols

**4. Cross-cutting Patterns** → `CLAUDE.md` or new skill
- Encoding utilities
- i18n conventions
- Accessibility requirements

### Diff Analysis

Compare extracted patterns against current documentation:
```
Pattern: "Set mutation after spreading for React state"
├── Found in: 07-comprehensive-ego-gift-list/findings.md
├── Current skill: frontend-dev-guidelines/component-patterns.md
└── Status: MISSING - needs to be added
```

---

## Step 3: PROPOSE - Generate Updates

### Output Format

```markdown
## Findings Sync Report

### Summary
- Findings scanned: 8 (06-01 through 06-08)
- New patterns found: 6
- Already documented: 2
- Conflicts: 0

### Proposed Updates

#### 1. frontend-dev-guidelines/resources/component-patterns.md
**Add section: State Mutation Safety**
```typescript
// CORRECT: Create new Set for React state updates
setSelectedIds(prev => new Set([...prev, newId]))

// WRONG: Mutating existing Set
selectedIds.add(newId) // React won't detect change
setSelectedIds(selectedIds)
```
Source: 07-comprehensive-ego-gift-list/findings.md

#### 2. CLAUDE.md
**Add to Pre-Write Checklist:**
- `accessibilityChecked` - Verify keyboard nav, ARIA labels before implementation
Source: 05-start-gift/findings.md, 07-comprehensive-ego-gift-list/findings.md
```

---

## Step 4: APPLY - Update Documentation

### Application Order
1. **CLAUDE.md** - Global rules first
2. **SKILL.md** files - Domain-specific guidance
3. **resources/*.md** - Detailed patterns

### Conflict Resolution
If a pattern conflicts with existing documentation:
1. Keep the more recent/specific version
2. Note the conflict in the sync report
3. Ask user for resolution if critical

---

## Pattern Extraction Templates

### From "Key Learnings"
```
Finding: "React Compiler eliminates manual optimization"
→ Skill Update: component-patterns.md
→ Content: Remove references to manual useCallback/useMemo
```

### From "Pattern Recommendations"
```
Finding: "Document hover-overlay pattern: TierLevelSelector + enhancement selector"
→ Skill Update: common-patterns.md
→ Content: New section "Hover Overlay Pattern" with code example
```

### From "Things to Watch"
```
Finding: "State synchronization risk: Enhancement state exists in multiple places"
→ Skill Update: component-patterns.md
→ Content: Warning box about state duplication anti-pattern
```

---

## Automation Hooks (Future)

### Option A: Post-task-code Hook
```typescript
// After /task-code completes, if findings.md was created/modified:
// → Prompt: "New findings detected. Run /findings-sync?"
```

### Option B: Pre-commit Check
```bash
# If findings.md in staged files:
# → Run sync analysis, warn if patterns not reflected in skills
```

---

## Quick Reference

### Commands
- `/findings-sync` - Run full sync workflow
- `/findings-sync --dry-run` - Show proposed changes only
- `/findings-sync --from=06` - Sync only from specific directory prefix

### Related Files
- `docs/**/findings.md` - Source of patterns
- `.claude/skills/*/SKILL.md` - Target documentation
- `.claude/skills/*/resources/*.md` - Detailed pattern docs
- `CLAUDE.md` - Global project rules

---

## See Also
- [skill-developer](../skill-developer/SKILL.md) - For creating new skills
- [frontend-dev-guidelines](../frontend-dev-guidelines/SKILL.md) - Primary frontend target
- [backend-dev-guidelines](../backend-dev-guidelines/SKILL.md) - Primary backend target
