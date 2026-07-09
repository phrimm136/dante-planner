# Publish Functionality - Learning Reflection

## What Was Easy

- Existing button infrastructure: isPublishing flag and button structure already wired, just needed handler logic
- Clear API pattern to follow: update() method provided direct template for togglePublish() structure
- Backend contract established: PUT endpoint already implemented; frontend just needed to connect
- Save handler pattern proven: handleSave() demonstrated save-then-X flow; repurposed for save-then-publish seamlessly
- Schema alignment: ServerPlannerResponseSchema validation pattern directly applicable to new published field

## What Was Challenging

- Missing published state initialization: Button showed wrong text until response data was extracted; required adding published field to PlannerMetadata type interface
- i18n key path mismatch: Code path differed from expected structure; required creating all 4 language files with correct nested keys
- Error-specific user feedback: Generic error handling insufficient; required parsing HTTP status codes (403/404/429) to show context-appropriate messages
- Multi-point state synchronization: Published state had to flow consistently through backend response → metadata → component state across four different operations (create, update, load, reload)
- No spec-to-implementation examples: Plan documented what to do but not where exactly state updates lived; required reading multiple files to trace data flow

## Key Learnings

- Idempotent endpoints enable toggle patterns: Backend toggle means client tracks state from responses, not from request intent; button text derivation must come from server truth
- Pre-operation validation is critical: Save-before-publish flow prevents publishing stale content but requires explicit early return on save failure; implicit success assumption leads to bugs
- i18n structure matters architecturally: 16 keys (4 languages × 4 keys) is significant; adding keys in wrong location or with inconsistent nesting breaks discoverability
- HTTP status code matters to UX: Users distinguish "not found" from "forbidden" from "rate limited" differently; generic error toast is unacceptable for authenticated operations
- State extraction requires consistency patterns: When multiple operations return same schema, state must extract from response at single location to avoid drift (learned via initial published state bugs)

## Spec-Driven Process Feedback

- Research.md was architecturally accurate: Pattern mapping to existing update() and handleSave() worked without deviation; no surprises in implementation
- Plan.md execution order was logical but incomplete: Steps 1-3 executed cleanly but Step 4 (published state tracking) emerged as critical mid-implementation; original plan assumed this would surface earlier
- Instructions.md ambiguities resolved correctly: Decision to track published state and show toggle text aligned with spec intent ("View button text that changes based on publishing state")
- Backend contract verification was essential: Confirming published field existed in response before schema change prevented implementation detours
- Missing: Error message specification: Review discovered error parsing needed 3 specific HTTP codes; instructions didn't enumerate error scenarios requiring localized messages

## Pattern Recommendations

- Document save-before-X pattern explicitly: Save-then-publish, save-then-export, save-then-share all follow same shape; create reusable async orchestration pattern for fe-component skill
- Create error status parser utility: HTTP error detection (403/404/429) appears across endpoints; extract to shared lib/errorHandling.ts to prevent duplication
- Establish i18n namespace conventions: Current pages.plannerMD.{feature}.{status} works but could conflict; document reserved namespaces to prevent collisions as app scales
- Define state extraction conventions: When APIs return rich responses (like ServerPlannerResponse), document single-source-of-truth extraction point; prevents subtle sync bugs with duplicated state
- Formalize response schema extensions: Adding published to existing schema worked but should have pre-defined hook pattern for backward compatibility checks

## Next Time

- Verify schema includes all response fields before planning: Backend contract review should confirm every field consumer code will reference exists in frontend schema
- Map error scenarios first: Identify specific HTTP codes and i18n messages before implementing error handlers; prevents mid-implementation error message churn
- Extract state synchronization points upfront: Plan which operations return state and where it flows; reduces bug discovery during testing
- Test published state initialization on existing planners: Original plan focused on new planner flow; editing existing published planners surfaced state sync requirements
- Create error scenario test checklist early: 403/404/429 error paths need explicit testing; current plan only mentioned existence; should include expected toast message in verification checkpoints
