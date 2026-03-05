# Dante's Planner

Game planning and management tool for Limbus Company.

## Project Architecture

- **Frontend**: React + TypeScript + TanStack Query + shadcn/ui
- **Backend**: Spring Boot + Java
- **Data**: Static JSON files with runtime validation

---

## CRITICAL RULES

1. **NEVER modify unrequested code** - Only change what was explicitly asked. Your "kindness" and "helpfulness" DESTROY the entire codebase. Do NOT refactor surrounding code, do NOT add improvements, do NOT fix unrelated issues. Surgical precision ONLY.
2. **NEVER hardcode values** - Use constants files
3. **NEVER skip pattern check** - Read similar files first
4. **NEVER mix concerns** - Separate layers
5. **ALWAYS validate data** - Zod (FE), Jakarta Validation (BE)
6. **ALWAYS follow existing patterns** - Consistency over cleverness
7. **ALWAYS extract duplicates** - If similar code exists, refactor into shared utility/component
8. **BUG FIX - When the user is compalining about a bug: Read working → Read broken → State root cause → THEN fix**
9. **BE CONCISE** - No unnecessary explanations or verbose output
10. **NO inline annotations** - Don't add `(added for X)` or `(changed from Y)` comments; code is self-documenting, git tracks changes
11. **NEVER circumvent errors** - Always resolve errors; do not bypass or work around them
12. **NO FUCKING EXCLAMATION MARKS** - Never use exclamation marks in responses. Period.

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

## Code Intelligence

LSP is enabled for both languages. Prefer LSP over grep for navigation.

| Operation | Use for |
|-----------|---------|
| `goToDefinition` | Jump to symbol declaration |
| `findReferences` | All usages across codebase |
| `hover` | Type signatures, docs |
| `documentSymbol` | All symbols in a file |
| `incomingCalls` / `outgoingCalls` | Call graph traversal |

- **TypeScript**: `typescript-language-server` — covers `.ts`, `.tsx`
- **Java**: `jdtls` — covers `.java`; stale cache at `~/.cache/jdtls/` → `rm -rf` the erroring workspace dir and restart session

---

## Code Review

Run `code-review-orchestrator` agent when all tasks complete.
See `/task-run` for detailed review workflow.

Verdicts: REJECT / NEEDS WORK / ACCEPTABLE

---

## Git Workflow

- Format: `type: description` (e.g., `feat: add identity card`)
- One logical change per commit
- Only commit when tests/builds pass
- **NO Claude signature** - Do not add "Co-Authored-By: Claude" to commits

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
