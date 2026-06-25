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
3. **ALWAYS follow existing patterns** - Read similar files first, then match. Consistency over cleverness.
4. **NEVER mix concerns** - Separate layers
5. **ALWAYS validate data** - Zod (FE), Jakarta Validation (BE)
6. **ALWAYS extract duplicates** - If similar code exists, refactor into shared utility/component
7. **BUG FIX** - Read working → Read broken → State root cause → Resolve. NEVER circumvent or bypass errors.
8. **BE CONCISE** - No unnecessary explanations or verbose output
9. **NO inline annotations, NO throwaway code** - Don't add `(added for X)` or `(changed from Y)` comments; code is self-documenting, git tracks changes. Don't write comments that restate what the function name already says. Don't add debug prints or verbose formatting you'll remove next edit. Write final-form code on the first Write.
10. **NO FUCKING EXCLAMATION MARKS** - Never use exclamation marks in responses. Period.
11. **NEVER cd** - Use in-command directory flags (`--cwd`, `-p`, `-C`, etc.) to target subdirectories. `cd` confuses the session's working directory and compound commands with `cd` get blocked by security evaluation.

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

Prefer LSP over Grep/Read for code navigation — it's faster, precise, and avoids reading entire files:
- `workspaceSymbol` to find where something is defined
- `findReferences` to see all usages across the codebase
- `goToDefinition` / `goToImplementation` to jump to source
- `hover` for type info without reading the file

Use Grep only when LSP isn't available or for text/pattern searches (comments, strings, config).

After writing or editing code, check LSP diagnostics and fix errors before proceeding.

- **TypeScript**: `typescript-language-server` — covers `.ts`, `.tsx`
- **Java**: `jdtls` — covers `.java`; stale cache at `~/.cache/jdtls/` → `rm -rf` the erroring workspace dir and restart session

---

## Code Review

Run `code-review-orchestrator` agent when all tasks complete.
See `/task-run` for detailed review workflow.

Verdicts: REJECT / NEEDS WORK / ACCEPTABLE

- **Validate findings before applying** — reviewers can be confidently wrong about project conventions or runtime contracts. For each finding: confirm it at the cited file:line, check the "fix" actually improves correctness, verify config/API claims via dry-run/tests/grep. State a verdict per finding (Fix / Skip — reason / Confirmation); never silently drop one.
- **Behavior-preserving refactors — diff findings against the original.** For a rename/move/split, a "this changed behavior" or "this introduced disorder" finding is only real if it differs from the pre-change source. Use `git show HEAD:<path>` on the moved/deleted original (or the consumer's prior import block) to classify each finding as regression-vs-preserved. A faithful move that carries a pre-existing wart forward did not *introduce* it — surgical precision (rule #1) means leave it.
- **Large diffs (~15+ files):** `code-review-orchestrator` can fail "Prompt is too long" (it fans out to ~5 parallel reviewers). Fall back to a single focused `code-architecture-reviewer` scoped to the highest-risk files.

---

## Git Workflow

See `commit-process` skill for the full workflow (validation, branching, message writing, staging, checkout).

---

## Import Conventions

| Domain | Alias | Resolves To |
|--------|-------|-------------|
| Frontend | `@/` | `frontend/src/` |
| Frontend | `@static/` | `static/` (has own `CLAUDE.md` — read before touching data, images, or UI layout) |
| Backend | - | `org.danteplanner.backend.{domain}` |

---

## File Naming

| Type | Frontend | Backend |
|------|----------|---------|
| Component/Controller | `PascalCase.tsx` | `PascalCaseController.java` |
| Hook/Service | `useCamelCase.ts` | `PascalCaseService.java` |
| Type/DTO | `PascalCaseTypes.ts` | `PascalCaseDto.java` |
| Schema/Entity | `PascalCaseSchemas.ts` | `PascalCase.java` |
