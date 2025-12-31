# Skill Development - Educational Reference

This document explains **why** LimbusPlanner uses specific skill patterns. For actionable rules, see the `skill-developer` skill.

---

## Why Skills?

Skills provide context-aware guidance to Claude Code. Instead of loading all documentation upfront, skills activate only when relevant, reducing token usage and focusing Claude's attention.

---

## Two-Hook Architecture

**1. UserPromptSubmit Hook**
- Triggers BEFORE Claude sees the prompt
- Suggests relevant skills based on keywords
- Injects skill as context (stdout → Claude's input)

**2. Stop Hook (Error Handling Reminder)**
- Triggers AFTER Claude responds
- Gentle reminders without blocking workflow
- Used for quality awareness (e.g., error handling)

---

## Skill Types

| Type | Enforcement | Use Case |
|------|-------------|----------|
| Guardrail | `block` | Critical mistakes, data integrity |
| Domain | `suggest` | Best practices, how-to guides |

**Block** physically prevents Edit/Write until skill is used.
**Suggest** injects reminder, doesn't block.

---

## Trigger Philosophy

### Keywords
Explicit topic mentions. User says "component" → fe-component activates.

### Intent Patterns
Implicit action detection via regex. User says "create a new page" → matches `(create|add).*?(page|route)`.

### File Paths
Location-based activation. Editing `*Controller.java` → be-controller activates.

### Content Patterns
Technology detection in file contents. File contains `@sentry` → error-tracking activates.

---

## Skip Conditions

| Method | Purpose | Use Case |
|--------|---------|----------|
| Session tracking | Don't repeat in same session | Automatic |
| File markers | Permanent skip for verified files | `// @skip-validation` |
| Env vars | Emergency disable | `SKIP_SKILL_GUARDRAILS=true` |

---

## Best Practices

### 500-Line Rule
Keep SKILL.md under 500 lines. Use reference files for details.

### Progressive Disclosure
Main skill → Quick reference
Reference files → Deep details

### Description Keywords
Include all trigger terms in the description field (max 1024 chars). Claude Code uses this for matching.

### Gerund Naming
Prefer verb + -ing form: `processing-pdfs`, `error-tracking`.

---

## Hook Exit Codes

| Exit Code | Effect |
|-----------|--------|
| 0 | Allow, output shown to Claude |
| 2 | Block tool execution |
| Other | Allow, output shown to Claude |

---

## Further Reading

- [Claude Code Skills Documentation](https://docs.anthropic.com/claude-code/skills)
- `.claude/skills/skill-rules.json` - Master configuration
- `.claude/hooks/` - Hook implementations
