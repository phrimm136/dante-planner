# Findings: Intro/Outro Notes with Empty Note Visibility

## What Was Easy
- Utility extraction from duplicated isEmpty logic in two dialogs to shared noteUtils.ts
- Tiptap recursive node traversal — straightforward once node structure understood
- i18n key addition — mechanical pattern across 4 language files
- Schema version propagation — constants, entity default, config properties all mechanical
- Reusing existing NoteEditor, PlannerSection, and progressive reveal patterns

## What Was Challenging
- Research claimed "no controller/service changes needed" — wrong: DB-loaded entities skip @Builder.Default, requiring explicit setSchemaVersion() in update path
- Duplicate load paths: store's initializeFromPlanner vs PlannerMDEditPage's initialState both transform sectionNotes independently, causing v1 crash when only one was fixed
- Structural leaf nodes (hardBreak) incorrectly treated as empty — needed type discrimination in recursive traversal
- Viewer gating pattern divergence: editor uses numeric threshold, viewers use boolean arrays
- Existing isEmpty checks didn't strip whitespace — new util needed text trimming for spec compliance

## Key Learnings
- Schema versioning is server-controlled; @Builder.Default only applies to entity construction, not DB hydration — update paths must explicitly set version
- Load path consolidation: state init and persisted data load must merge through the same factory to avoid silent data shape mismatches
- Recursive node traversal needs type guards — structural nodes (hardBreak, image) should short-circuit emptiness checks
- Backward compatibility requires the utility itself to be v1-aware (isNoteEmpty(undefined) === true), not just optional chaining at call sites
- Extract shared utils after finding the 2nd duplication, not the 3rd — reduces divergent implementations

## Spec-Driven Process Feedback
- Research mapping 95% accurate — only missed schemaVersion service-layer requirement
- Plan execution order held — phases 1-5 dependency chain required no backtracking
- Instructions test checklist (25 manual steps) caught critical paths early
- Risk mitigation section correctly predicted v1 compatibility trap

## Pattern Recommendations
- Add load path consistency checklist: "If state init uses factory X, verify all load-from-storage paths also use X"
- Document @Builder.Default limitation: only applies to new entities, not DB-loaded ones
- Establish node type guard pattern for recursive tree walkers
- Clarify gating divergence: numeric thresholds (editor) vs boolean flags (viewers)

## Next Time
- Trace entity creation → builder defaults → update paths during research phase, not implementation
- Test v1 compatibility with fixture data BEFORE implementation to catch load path issues early
- Spot-check each phase's output is consumable by the next phase before proceeding
