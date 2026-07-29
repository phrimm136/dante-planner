# Findings: Identity Card Level and Name Display

## What Was Easy

- Pattern reuse from EGOCard Layer 5 structure - straightforward copy
- Line break rendering via JSX split/map - simpler than CSS approaches
- Suspense integration followed existing EGOName pattern exactly
- Two-line skeleton fallback matched content height immediately
- MAX_LEVEL constant already existed - no hunting for magic numbers

## What Was Challenging

- Overlay stacking ambiguity - required explicit visual stacking order confirmation
- i18n edge case (trailing newlines) - spec didn't specify handling rules
- Gradient background rationale - frame already provides dark gradient, required decision
- IdentityName scope - initially unclear if it or parent should handle line breaks

## Key Learnings

- Spec-to-pattern mapping before coding prevents false starts and refactoring
- Stacking order is visual, not just code - explicit Layer 1-5 hierarchy prevents z-index bugs
- JSX line break rendering beats CSS white-space - testable, semantic, handles clamping
- Granular Suspense (name only) beats global fallback - card stays visible during language switch
- Test coverage drives spec clarity - checkpoints forced edge case clarification
- Constants centralization prevents duplication - single source of truth

## Spec-Driven Process Feedback

- Research.md mapping was 95% accurate - only missing trailing newline handling
- Execution order (IdentityName → IdentityCard) was correct - isolated component first
- Gap analysis correctly flagged all needed changes - no surprises
- Technical constraints stacking order diagram saved time - caught z-index issues upfront

## Pattern Recommendations

- Add to fe-component skill: Granular Suspense for async i18n updates
- Document visual stacking order as required spec section for layered UI
- Consider reusable InfoPanel component pattern (EGOCard + IdentityCard share it)
- Clarify edge-case handling rules in spec (trailing newlines, malformed input)

## Next Time

- Require visual diagram in spec - annotated screenshot prevents ambiguity
- Explicitly call out stacking/z-index assumptions upfront
- Batch related edge case tests together (name variants)
- Validate i18n format before spec freeze - verify actual data
- Add "diff assumptions" section to research.md for pattern deviations
