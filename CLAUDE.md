# LimbusPlanner

Game planning and management tool for Limbus Company.

See markdown files in `/docs` for detailed project specification.

## Project Architecture

- **Frontend**: React + TypeScript + TanStack Query + shadcn/ui
- **Backend**: Spring Boot + Java
- **Data**: Static JSON files with runtime validation

## 🚫 UNIVERSAL CRITICAL RULES

1. **NEVER hardcode values** - Use constants files
2. **NEVER skip pattern check** - Read similar files first
3. **NEVER mix concerns** - Separate layers (Controller/Service/Repository or Component/Hook/Data)
4. **ALWAYS validate data** - Runtime validation required (Zod for FE, Jakarta Validation for BE)
5. **ALWAYS follow existing patterns** - Consistency over cleverness

## Domain-Specific Guidelines

When working in specific domains, Claude will automatically load:

- **Frontend** (`frontend/`): See [frontend/CLAUDE.md](frontend/CLAUDE.md)
- **Backend** (`backend/`): See [backend/CLAUDE.md](backend/CLAUDE.md)

---

## MANDATORY Pattern Check Process

Before writing ANY new file or editing ANY existing code:

1. **Load skill documentation** and relevant resource files
2. **Search for similar existing files** using Glob/Grep
3. **Read at least 1-2 similar files** to understand patterns
4. **If patterns conflict**: Skill/resource documentation takes precedence over similar files
5. **State pattern reference**:
   - "**Pattern Reference:** [filename] - using [specific patterns]"
   - "**Skill Compliance:** Following [resource.md] - [specific requirement]"
6. **If no pattern exists** in skill/resource/code: "**New Pattern:** [reason]"

---

## Intent Output Format

**Before EVERY Write/Edit, you MUST state:**

```
**Intent for [filename]:**
- WHAT: [Creating new file / Adding function X / Modifying Y / Removing Z]
- WHY: [Purpose - e.g., "to handle user selection state", "to fix missing validation"]
- HOW: [For modifications - "changes behavior from X to Y" / For new code - skip this]
```

---

## Pre-Write Checklist

Before Write/Edit, verify:
- `skillLoaded` - Used Skill tool for domain
- `resourceRead` - Read relevant resource files
- `constantsChecked` - Checked constants file (if using values)
- `patternChecked` - Read similar existing files
- `intentStated` - Stated intent for the change

**STOP CONDITIONS:** If any required flag is false → complete that step first

---

## Code Review (Agent-Based)

**Review happens ONCE after all TODO items are completed**, not per-edit.

### When to Run Review

Run the `code-architecture-reviewer` agent when:
- ALL tasks in TodoWrite are marked complete
- Using `/task-run` command → automatically triggers at completion
- Manually via `/task-review` command

### How to Run

```
Task tool → subagent_type: "code-architecture-reviewer"
prompt: "Batch review: [list of files changed] - [task summary]"
```

### Reviewer Verdicts
- 🔴 **REJECT**: Critical issues - MUST fix before commit
- 🟠 **NEEDS WORK**: Major issues - SHOULD fix before commit
- 🟢 **ACCEPTABLE**: Minor or no issues - can commit

### Skip Review For
- Trivial edits (typos, formatting, comments only)
- Deleting code
- Configuration-only changes

---

## Git Workflow

### Commit Rules
- Only commit when tests/builds pass
- One logical change per commit
- Clear, descriptive commit messages
- Format: `type: description` (e.g., `feat: add identity card component`)

### Branch Strategy
- `main` - production-ready code
- Feature branches from `main`
- PR required for merging to `main`

---

## Import Conventions (All Code)

### Frontend
- `@/` → `frontend/src/`
- `@static/` → `static/`

### Backend
- Use standard Java package imports
- Follow package structure: `com.limbusplanner.{domain}.{layer}`

---

## File Naming Standards

### Frontend (TypeScript/React)
- Components: `PascalCase.tsx`
- Hooks: `useCamelCase.ts`
- Utilities: `camelCase.ts`
- Types: `PascalCaseTypes.ts`
- Schemas: `PascalCaseSchemas.ts`

### Backend (Java/Spring)
- Controllers: `PascalCaseController.java`
- Services: `PascalCaseService.java`
- Repositories: `PascalCaseRepository.java`
- DTOs: `PascalCaseDto.java`
- Entities: `PascalCase.java` (JPA entities)

---

## Documentation Standards

- Update relevant docs when changing behavior
- Use JSDoc (frontend) or JavaDoc (backend) for public APIs
- Keep `docs/` folder up to date with architectural decisions
