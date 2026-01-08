# LimbusPlanner

Game planning and management tool for Limbus Company.

## Project Architecture

- **Frontend**: React + TypeScript + TanStack Query + shadcn/ui
- **Backend**: Spring Boot + Java
- **Data**: Static JSON files with runtime validation

## CRITICAL RULES

1. **NEVER hardcode values** - Use constants files
2. **NEVER skip pattern check** - Read similar files first
3. **NEVER mix concerns** - Separate layers
4. **ALWAYS validate data** - Zod (FE), Jakarta Validation (BE)
5. **ALWAYS follow existing patterns** - Consistency over cleverness
6. **ALWAYS extract duplicates** - If similar code exists, refactor into shared utility/component
7. **BUG FIX - When the user is compalining about a bug: Read working → Read broken → State root cause → THEN fix**
8. **BE CONCISE** - No unnecessary explanations or verbose output
9. **NEVER modify unrequested code** - Only change what was explicitly asked
10. **NO inline annotations** - Don't add `(added for X)` or `(changed from Y)` comments; code is self-documenting, git tracks changes

---

## REQUIRED OUTPUT FORMAT

**Every Write/Edit MUST include these sections:**

### 1. SEARCH LOG
```
SEARCH: [keywords]
LOCATIONS: [where searched]
FOUND: [file:function] or "No match"
DECISION: Using existing / Creating new because [reason]
```

### 2. PATTERN LOG (new files)
```
PATTERN SOURCE: [filename]
READ FILE: [path read]
APPLYING: [patterns copied]
```

### 3. INTENT
```
**Intent for [filename]:**
- WHAT: [action]
- WHY: [purpose]
```

**INCOMPLETE if SEARCH LOG or PATTERN LOG missing.**

---

## Domain Detection (MANDATORY)

**Before writing ANY code, detect domain and load context:**

```
1. Detect domain from file path:
   - frontend/* → Read frontend/CLAUDE.md
   - backend/*  → Read backend/CLAUDE.md

2. Load appropriate skill (see table below)

3. State: "Domain: [frontend|backend], Skill: [skill-name]"
```

### Frontend (`frontend/`)

**MUST Read:** `frontend/CLAUDE.md` before any frontend work

| Task | Skill |
|------|-------|
| Components, UI, styling | `fe-component` |
| Hooks, schemas, data fetching | `fe-data` |
| Pages, routes, navigation | `fe-routing` |
| Tests | `fe-testing` |

### Backend (`backend/`)

**MUST Read:** `backend/CLAUDE.md` before any backend work

| Task | Skill |
|------|-------|
| Controllers, endpoints, DTOs | `be-controller` |
| Services, repositories, entities | `be-service` |
| Security, auth, JWT, CORS | `be-security` |
| WebSocket, async, exceptions | `be-async` |
| Tests | `be-testing` |
| Configuration, properties | `be-config` |

**Usage:** `Skill tool: [skill-name]` (e.g., `Skill tool: fe-component`)

---

## Code Review

Run `code-architecture-reviewer` agent when all tasks complete.
See `/task-run` for detailed review workflow.

Verdicts: REJECT / NEEDS WORK / ACCEPTABLE

---

## Git Workflow

- Format: `type: description` (e.g., `feat: add identity card`)
- One logical change per commit
- Only commit when tests/builds pass

---

## Import Conventions

| Domain | Alias | Resolves To |
|--------|-------|-------------|
| Frontend | `@/` | `frontend/src/` |
| Frontend | `@static/` | `static/` |
| Backend | - | `org.danteplanner.backend.{domain}` |

---

## File Naming

| Type | Frontend | Backend |
|------|----------|---------|
| Component/Controller | `PascalCase.tsx` | `PascalCaseController.java` |
| Hook/Service | `useCamelCase.ts` | `PascalCaseService.java` |
| Type/DTO | `PascalCaseTypes.ts` | `PascalCaseDto.java` |
| Schema/Entity | `PascalCaseSchemas.ts` | `PascalCase.java` |
