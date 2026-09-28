# Skill Test Patterns

## 1. Trigger Tests

Verify the skill activates for the right prompts and skips unrelated ones. The frontmatter
description is the only trigger, so phrase a prompt with its keywords and one without, and check
which one loads the skill.

## 2. Functional Tests

Verify correct output when the skill activates. Manual checklist:

- [ ] SKILL.md loaded into system prompt context
- [ ] Generated skill follows kebab-case folder naming
- [ ] YAML frontmatter includes `name` and `description` fields
- [ ] `description` is under 1024 chars with no XML angle brackets
- [ ] L3 references/ file created for detail content if SKILL.md approaches 500 lines

## 3. Comparison Tests

Verify skill improves output vs. baseline. Run the same prompt with and without the skill active.

| Prompt | Without Skill | With Skill | Expected Improvement |
|--------|--------------|------------|---------------------|
| "create a skill for X" | Ad-hoc SKILL.md | Correct structure | Pattern compliance |
| "how do I test my skill" | Generic answer | Three-area test plan (trigger/functional/comparison) | Structured verification |

## Test Order

1. Trigger tests first — two prompts, one with the keywords and one without
2. Functional verification — manual, per-skill checklist
3. Comparison tests — for new skills only, establish baseline before activating
