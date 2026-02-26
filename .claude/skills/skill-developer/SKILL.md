---
name: skill-developer
description: Create and manage Claude Code skills. Use when creating skills, modifying skill-rules.json, or debugging activation.
---

# Skill Developer

## Rules

- **Under 500 lines** - Use reference files for details
- **Include trigger keywords in description** - Max 1024 chars, no XML brackets
- **Lowercase hyphenated names** - `my-new-skill`
- **Three-level disclosure** - L1: frontmatter, L2: SKILL.md, L3: `references/`
- **Test before documenting** - Verify trigger + functional + comparison

## Three-Level Disclosure

| Level | Location | Loads When | Purpose |
|-------|----------|------------|---------|
| L1 | YAML frontmatter | Always (every request) | Trigger detection only |
| L2 | SKILL.md body | Skill matches | Instructions + templates |
| L3 | `references/*.md` | Explicitly linked | Deep detail, tables, examples |

## Folder Structure

```
skill-name/
├── SKILL.md            (required — L1 + L2)
├── references/         (optional — L3 detail files)
│   ├── trigger-guide.md
│   └── test-patterns.md
├── scripts/            (optional — executable helpers)
└── assets/             (optional — images, data files)
```

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
- Triggers: `references/trigger-guide.md`
- Tests: `references/test-patterns.md`
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

See `references/trigger-guide.md` for full trigger types, enforcement levels, priority levels, and `agentConfig` fields.

## Testing

Three areas — run in order:

1. **Trigger** — skill loads for right prompts, skips unrelated ones
2. **Functional** — correct output when skill activates (checklist)
3. **Comparison** — skill vs. baseline improvement (new skills only)

```bash
# Trigger test
echo '{"prompt":"your test prompt"}' | npx tsx .claude/hooks/skill-activation-prompt.ts

# Validate JSON
jq . .claude/skills/skill-rules.json
```

See `references/test-patterns.md` for functional checklist and comparison test table.

## File Locations

| File | Purpose |
|------|---------|
| `.claude/skills/{name}/SKILL.md` | Skill content (L1 + L2) |
| `.claude/skills/{name}/references/` | Detail files (L3) |
| `.claude/skills/{name}/scripts/` | Helper scripts |
| `.claude/skills/skill-rules.json` | Trigger config |
| `.claude/hooks/` | Hook implementations |
| `.claude/settings.json` | Hook registration |

## Reference

- Config: `.claude/skills/skill-rules.json`
- Hooks: `.claude/hooks/skill-activation-prompt.ts`
- Triggers: `references/trigger-guide.md`
- Tests: `references/test-patterns.md`
- Why: `docs/learning/skill-development.md`
