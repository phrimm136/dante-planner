# Learning Reflection: Micro Suspense Pattern Implementation

## What Was Easy
- Hook separation pattern well-established by Identity detail page reference
- Query key structure and naming conventions clear from existing pattern
- Wrapper component pattern straightforward (fetch i18n → pass to base component)
- Schema validation already in place; adding maxEnhancement field was straightforward
- TanStack Query caching handled automatically once keys matched correctly

## What Was Challenging
- Fallback rendering created invisible failures: Wrapper components rendered AllEnhancementsPanel in fallback, which calls useSkillTagI18n for description formatting. This suspended the entire parent tree despite claiming isolation.
- Silent Suspense bubbling: Root Suspense continued triggering despite seemingly correct Suspense boundary placement. No error message; just wrong behavior.
- Wrapper pattern ambiguity: Initial implementation followed IdentityHeaderWithI18n (external Suspense around wrapper) instead of PassiveCardI18n (internal Suspense inside wrapper). Pattern source wasn't explicit about which to use.
- Multiple simultaneous issues masked root cause: shell using i18n and wrong wrapper pattern stacked, making it harder to isolate which change actually fixed each symptom.

## Key Learnings
- Fallback components must NOT use hooks: If fallback renders actual UI (not just empty state), it can trigger new suspensions. Empty strings/arrays/objects are safer than rendering incomplete components.
- Internal vs external Suspense matters: Internal Suspense (boundary inside wrapper) isolates child suspension from parent. External Suspense (boundary around wrapper) means wrapper itself suspends.
- Spec-only and i18n-only separation requires data schema alignment: Had to add maxEnhancement to spec JSON to avoid shell depending on i18n. Pattern wasn't just about query keys—data schema had to support the split.
- Query keys determine fetch behavior: Identical keys across hooks prevent double-fetch. Different keys for spec vs i18n allowed independent caching.
- Wrapper components can still suspend their parents if fallback isn't safe: Just wrapping in Suspense doesn't isolate unless fallback is truly suspension-free.

## Why It Became a Mystery
- No systematic fallback analysis: The spec said "use fallbacks" but didn't explicitly forbid using components with hooks. The real culprit (FormattedDescription in fallback) went unnoticed for iterations.
- Didn't isolate by removing sections: Testing removed entire wrapper components, but didn't test removing FormattedDescription specifically from the fallback. Surgical debugging would've found it instantly.
- Assumed wrapper pattern was the issue: Spent iterations rewriting wrapper structure before testing fallback content. The wrapper placement was actually correct eventually; the fallback was the problem.
- Didn't trace the suspension source: Root Suspense triggered, but debugging didn't print stack traces or component names to track exactly which hook suspended. Would've caught useSkillTagI18n immediately.
- Pattern source didn't show negative examples: Identity patterns didn't have this issue because their fallbacks were truly empty (no components rendering). Didn't know to check this assumption.
- Trial-and-error iteration without hypotheses: Each fix tested without first confirming the previous fix was effective. Changes stacked making causality unclear.

## Spec-Driven Process Feedback
- Research.md was accurate but incomplete: Correctly identified patterns and file locations, but didn't explicitly state "fallback components cannot call hooks"
- Plan.md didn't account for fallback rendering behavior: Assumed placing Suspense boundaries would isolate suspension without analyzing what the fallback would render
- Spec needed explicit constraint: Should've stated "Suspense fallback must render component with zero props or only spec data—never call hooks in fallback"
- Testing guidelines had right structure but missing root cause verification: Tests checked language switch behavior but didn't verify what component caused suspension

## Pattern Recommendations
- Add to skill docs: PassiveCardI18n pattern with internal Suspense boundary inside wrapper prevents wrapper from suspending parent
- Document fallback constraint: Suspense fallbacks must be inert—no hooks, no components with i18n, no queries. Use empty values.
- Create anti-pattern doc: Do NOT render component with hooks in Suspense fallback, even if that component is wrapped in its own Suspense elsewhere
- Add diagnostic pattern: For debugging suspension issues, print component stack in error boundary to trace exactly which hook triggered suspension

## Next Time
- Start with fallback audit: Before testing language switch behavior, verify every Suspense fallback contains zero hook calls and zero i18n dependencies
- Use hypothesis-driven debugging: Document each hypothesis and result before next iteration rather than trial-and-error
- Isolate by removing, not rewriting: Remove suspicious fallback content and test before rewriting patterns
- Instrument suspension points: Add logging at Suspense boundaries to confirm which boundary triggers and when
- Consult negative patterns first: For any architectural pattern, ask what makes this fail and review what other implementations avoided
