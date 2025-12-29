# Note Editor Learning Reflection

## What Was Easy
- Tiptap library integration with React 19 - StarterKit abstraction handled most formatting needs
- Controlled component pattern mapped smoothly to existing PlannerMDNewPage state lifting
- Custom Mark extension for spoilers - ProseMirror node/mark architecture intuitive once understood
- XSS prevention with sanitizeUrl utility scaled cleanly
- shadcn Dialog provided accessibility and styling without friction

## What Was Challenging
- Dynamic editable toggling with focus/blur - RAF complexity in timing wasn't obvious upfront
- 21 editors deployed vs 5 planned - scope creep bypassed MAX_NOTE_EDITORS constant
- Image adapter abstraction without production backend created migration debt
- Duplicate component systems (tiptap-ui-primitive vs shadcn/ui) rationale unclear
- Performance step (Step 13) deferred - 20 concurrent editors untested

## Key Learnings
- Tiptap extension architecture enables composability but requires upfront decisions
- JSONContent storage better than HTML but schema validation across 21 editors adds complexity
- Focus-based preview mode requires careful lifecycle management for blob URL cleanup
- Custom Mark extensions follow predictable patterns once node vs mark distinction internalized
- IImageUploadAdapter abstraction enables parallel development without Cloudflare dependency

## Spec-Driven Process Feedback
- research.md mapping accurate - all seven ambiguities identified and resolved upfront
- plan.md execution order worked well with bottom-up dependency layering
- Scope creep (5→21 editors) happened because plan didn't freeze integration checkpoint
- Step 13 deferral created acceptance testing gap - needs binding checkpoint before merge

## Pattern Recommendations
- Add "Tiptap Mark Extension Template" to skill docs with toggleCommand convention
- Document "Adapter Pattern for Async Resources" using IImageUploadAdapter as example
- Clarify "State Lifting Scope Boundaries" for detecting excessive component instances
- Add "Blob URL Lifecycle Management" patterns to prevent memory leaks

## Next Time
- Freeze component instance counts before integration - use MAX_* constants as hard gates
- Profile performance before merging - don't defer acceptance checkpoints
- Document component system choices upfront - dual UI systems need written rationale
- Run Step 13 performance verification before closing task
- Add hardcoded magic number detection to code review checklist
