# Trigger Reference Guide

## Trigger Types

| Type | Purpose | Example |
|------|---------|---------|
| `keywords` | Exact phrase match | `["create skill", "skill-rules.json"]` |
| `intentPatterns` | Regex on user intent | `"(create|add).*?skill"` |
| `pathPatterns` | File path glob | `"frontend/src/**/*.tsx"` |
| `contentPatterns` | File content regex | `"@sentry"` |
| `pathExclusions` | Glob exclude from fileTriggers | `"**/*.test.tsx"` |

## Enforcement Levels

| Level | Effect | Use Case |
|-------|--------|----------|
| `block` | Prevents Edit/Write until skill read | Critical guardrails (pattern compliance) |
| `suggest` | Injects reminder, doesn't block | Best practices, domain hints |
| `warn` | Low priority note | Rarely used |

## Skill Types

| Type | Purpose | Example |
|------|---------|---------|
| `guardrail` | Enforce patterns, block deviations | `fe-component`, `be-controller` |
| `domain` | Inject domain expertise | `skill-developer`, `error-tracking` |
| `agent` | Delegate to specialized agent | `code-architecture-reviewer` |

## Priority Levels

| Priority | Behavior | When to Use |
|----------|----------|-------------|
| `critical` | Always trigger on match | Never misfire tolerated |
| `high` | Trigger for most matches | Core guardrails |
| `medium` | Trigger for clear matches | Supplementary skills |
| `low` | Explicit prompts only | Rarely needed skills |

## agentConfig Fields

| Field | Type | Purpose |
|-------|------|---------|
| `preferOverWebSearch` | boolean | Use agent instead of raw WebSearch |
| `useContext7ForDocs` | boolean | Route official docs to Context7 MCP |
| `runAfterCodeWrite` | boolean | Auto-trigger post-implementation |
| `adversarialMode` | boolean | Assume code is flawed until proven |
