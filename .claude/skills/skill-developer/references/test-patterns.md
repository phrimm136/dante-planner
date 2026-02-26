# Skill Test Patterns

## 1. Trigger Tests

Verify skill activates for the right prompts, skips unrelated ones.

```bash
# Should trigger
echo '{"prompt":"create a new skill"}' | npx tsx .claude/hooks/skill-activation-prompt.ts
echo '{"prompt":"modify skill-rules.json"}' | npx tsx .claude/hooks/skill-activation-prompt.ts

# Should NOT trigger (verify no false positives)
echo '{"prompt":"fix a typo in the README"}' | npx tsx .claude/hooks/skill-activation-prompt.ts
echo '{"prompt":"run the tests"}' | npx tsx .claude/hooks/skill-activation-prompt.ts

# Validate config JSON
jq . .claude/skills/skill-rules.json
```

## 2. Functional Tests

Verify correct output when the skill activates. Manual checklist:

- [ ] SKILL.md loaded into system prompt context
- [ ] Generated skill follows kebab-case folder naming
- [ ] YAML frontmatter includes `name` and `description` fields
- [ ] `description` is under 1024 chars with no XML angle brackets
- [ ] `skill-rules.json` entry added with correct `type`, `enforcement`, `priority`
- [ ] L3 references/ file created for detail content if SKILL.md approaches 500 lines

## 3. Comparison Tests

Verify skill improves output vs. baseline. Run the same prompt with and without the skill active.

| Prompt | Without Skill | With Skill | Expected Improvement |
|--------|--------------|------------|---------------------|
| "create a skill for X" | Ad-hoc SKILL.md, no rules.json entry | Correct structure + trigger config | Pattern compliance |
| "add a trigger for Y" | Direct JSON edit | skill-rules.json with correct type/enforcement | Correct enforcement level |
| "how do I test my skill" | Generic answer | Three-area test plan (trigger/functional/comparison) | Structured verification |

## Test Order

1. Trigger tests first — fast, automated, catches misconfigured regexes
2. Functional verification — manual, per-skill checklist
3. Comparison tests — for new skills only, establish baseline before activating
