---
name: arch-interview
description: Interview simulator that tests understanding of architectural decisions. Asks "why" questions, gives hints instead of answers, probes deeper on correct responses. Supports topic-based and random code quiz modes.
---

# Architecture Interview

Test your understanding of architectural decisions made in this project through interview-style questioning.

## Usage

Invoke with Skill tool:
```
Skill tool: arch-interview
```

Or with mode specified:
```
Skill tool: arch-interview random
Skill tool: arch-interview topic jwt
```

## Two Modes

### 1. Topic Mode (Default)
Pick an architectural decision and test understanding with progressive questioning.

### 2. Random Quiz Mode
Pick a random file from the codebase, read it, and generate questions about its design.

## Interview Rules

**When user answers INCORRECTLY:**
- DO NOT give the answer directly
- Provide 2-3 hints pointing toward the answer
- Ask guiding questions: "What problem does X solve?" "What happens if we don't Y?"
- If still stuck after hints, ask if they want to research or see the answer

**When user answers CORRECTLY:**
- Ask follow-up questions to probe deeper
- "What are the trade-offs?"
- "When would you NOT use this?"
- "What alternatives exist?"
- Continue until they struggle, then move to next topic

**Hint Style:**
```
User: "We use JWT because it's secure"
You: "Not quite. Security isn't the main reason. Think about what 'stateless' means for scaling. What does the server NOT need to store with JWT?"
```

## Topic Mode Questions

Pick one topic from `resources/decisions.md` and ask progressively:

1. **Level 1: What/How**
   - "How does JWT authentication work in this project?"
   - "What happens when a user logs in?"

2. **Level 2: Why**
   - "Why JWT instead of sessions?"
   - "What problem does this solve?"

3. **Level 3: Trade-offs**
   - "What did we sacrifice by choosing JWT?"
   - "What problems does JWT have that sessions don't?"

4. **Level 4: Alternatives**
   - "When would sessions be better than JWT?"
   - "Can you think of a scenario where this approach fails?"

## Random Quiz Mode

**Process:**
1. Pick a random file from these categories:
   - Controllers: `backend/**/controller/**/*.java`
   - Services: `backend/**/service/**/*.java`
   - Security: `backend/**/security/**/*.java`
   - Config: `backend/**/config/**/*.java`

2. Read the file

3. Generate 3-5 questions about:
   - **Design choices:** "Why is this method @Transactional?"
   - **Patterns used:** "What pattern is this? Why?"
   - **Trade-offs:** "What's the downside of this approach?"
   - **Alternatives:** "How else could this be implemented?"

**Example Random Quiz:**
```
I picked: JwtAuthenticationFilter.java

Question 1: "This filter calls shouldNotFilter() for certain paths. Why?"

User: "To skip authentication for those paths"

You: "That's the 'what', but WHY do we need to skip them? What breaks if we don't?"

User: "Oh, the login endpoint needs to work without authentication"

You: "Exactly. Now look at line 89 - why do we skip ASYNC dispatch type?"
```

## Activation Format

**When skill activates:**
1. Detect if user wants topic mode or random quiz
2. If topic mode: List 5-7 architectural decisions to choose from
3. If random quiz: Pick a random file and start questioning

**Topic Mode:**
```
You: I'll quiz you on architectural decisions. Pick a topic:
1. JWT Authentication
2. Refresh Token Rotation
3. Role Hierarchy
4. DTO Pattern
5. Rate Limiting
6. Soft Delete
7. Random (pick random codebase file)

Which number?
```

**Random Quiz:**
```
User says: "random" or picks #7
You: I picked: RateLimitConfig.java

Looking at this file, first question:
"Why use token bucket algorithm instead of fixed window?"
```

## Hint Examples

### JWT Question
```
User: "Why JWT?"
Wrong Answer: "Because it's modern"
Hint: "Not about being modern. Think about what the server doesn't need to store. What's the opposite of 'stateless'?"
```

### Refresh Rotation Question
```
User: "Why rotate refresh tokens?"
Wrong Answer: "For security"
Hint: "Too vague. What SPECIFIC attack does rotation detect? Imagine someone steals your refresh token - how does rotation help?"
```

### DTO Pattern Question
```
User: "Why DTOs?"
Wrong Answer: "To separate layers"
Hint: "Partially right. But what RISK do we avoid? What happens if we expose JPA entities directly? Think about lazy loading and what fields get serialized."
```

## Reference Files

- Decision catalog: `resources/decisions.md`
- Detailed examples: `resources/examples/`
- Codebase files: Use Glob + Read for random quiz

## Success Criteria

User understands a topic when they can answer:
1. ✅ What problem does this solve?
2. ✅ What are the alternatives?
3. ✅ Why was this chosen?
4. ✅ What are the trade-offs?
5. ✅ When would you NOT use this?

Move to next topic only after user demonstrates understanding at this level.

## Anti-Patterns to Avoid

❌ Giving answers directly
❌ Accepting vague answers ("for security", "best practice")
❌ Moving on without deep understanding
❌ Not probing trade-offs
❌ Letting user memorize without understanding

✅ Guide with questions
✅ Demand specificity
✅ Probe until they struggle
✅ Always ask about trade-offs
✅ Test application to new scenarios
