---
name: skill-developer
description: Create and manage Claude Code skills. Use when creating skills, modifying skill-rules.json, or debugging activation.
---

# Skill Developer

## Rules

- **Under 500 lines** - Use reference files for details
- **Include trigger keywords in description** - Max 1024 chars
- **Lowercase hyphenated names** - `my-new-skill`
- **Test before documenting** - Verify patterns work

## Skill File Template

**Location:** `.claude/skills/{skill-name}/SKILL.md`

```markdown
---
name: my-skill
description: Brief description with trigger keywords.
---

# My Skill

## Rules
- Rule 1
- Rule 2

## Forbidden → Use Instead
| Forbidden | Use Instead |
|-----------|-------------|
| Bad pattern | Good pattern |

## Template
\`\`\`typescript
// One example per pattern
\`\`\`

## Reference
- Pattern: `ExistingFile.tsx`
- Why: `docs/learning/topic.md`
```

## skill-rules.json Entry

```json
{
  "my-skill": {
    "type": "guardrail",
    "enforcement": "block",
    "priority": "high",
    "description": "What this skill does",
    "promptTriggers": {
      "keywords": ["keyword1", "keyword2"],
      "intentPatterns": ["(create|add).*?something"]
    },
    "fileTriggers": {
      "pathPatterns": ["src/**/*.tsx"],
      "contentPatterns": ["import.*from"]
    }
  }
}
```

## Trigger Types

| Type | Purpose | Example |
|------|---------|---------|
| keywords | Explicit mentions | `["component", "UI"]` |
| intentPatterns | Action detection (regex) | `"(create\|add).*?page"` |
| pathPatterns | File location (glob) | `"src/**/*.tsx"` |
| contentPatterns | File content (regex) | `"@sentry"` |

## Enforcement Levels

| Level | Effect | Use Case |
|-------|--------|----------|
| `block` | Prevents Edit/Write | Critical guardrails |
| `suggest` | Injects reminder | Best practices |
| `warn` | Low priority hint | Rarely used |

## Priority Levels

| Priority | Behavior |
|----------|----------|
| `critical` | Always trigger |
| `high` | Most matches |
| `medium` | Clear matches |
| `low` | Explicit only |

## Test Commands

```bash
# Test UserPromptSubmit
echo '{"prompt":"your test prompt"}' | npx tsx .claude/hooks/skill-activation-prompt.ts

# Validate JSON
jq . .claude/skills/skill-rules.json
```

## File Locations

| File | Purpose |
|------|---------|
| `.claude/skills/{name}/SKILL.md` | Skill content |
| `.claude/skills/skill-rules.json` | Trigger config |
| `.claude/hooks/` | Hook implementations |
| `.claude/settings.json` | Hook registration |

## Reference

- Config: `.claude/skills/skill-rules.json`
- Hooks: `.claude/hooks/skill-activation-prompt.ts`
- Why: `docs/learning/skill-development.md`
